package dev.mocklab.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 独立脚本服务入口，监听任务并管理 Worker 进程池，以销毁进程强制终止超时脚本
 */
public final class Supervisor {
    /**
     * 父子进程协议的 JSON 编解码器，协议输出不能混入脚本控制台内容
     */
    private static final ObjectMapper JSON = new ObjectMapper();
    /**
     * 单个协议帧的字符数上限，防止无换行输入无限占用缓冲区
     */
    private static final int MAX_LINE = 5 * 1024 * 1024;
    /**
     * 固定工作进程数，由 RUNNER_POOL 指定，进程之间隔离脚本内存
     */
    private static final int POOL =
            Integer.parseInt(System.getenv().getOrDefault("RUNNER_POOL", "4"));
    /**
     * 单次工作进程交互的超时毫秒数，超时后强制销毁对应进程
     */
    private static final int TIMEOUT =
            Integer.parseInt(System.getenv().getOrDefault("SCRIPT_TIMEOUT_MS", "1000"));
    /**
     * 仅存放已完成预热且没有执行任务的进程槽位
     */
    private static final BlockingQueue<Slot> IDLE = new ArrayBlockingQueue<>(POOL);
    /**
     * 运行与排队任务总额度，防止请求在进程池前无限积压
     */
    private static final Semaphore CAPACITY = new Semaphore(POOL + 100, true);
    /**
     * 承载连接读取和进程交互的虚拟线程执行器，脚本并发仍由进程池限制
     */
    private static final ExecutorService TASKS = Executors.newVirtualThreadPerTaskExecutor();
    /**
     * 已经完成预热且尚未退役的进程数，包含正在执行任务的进程
     */
    private static final AtomicInteger LIVE = new AtomicInteger();

    /**
     * 预热工作进程并持续接受后端任务，进程随监督服务关闭而释放
     *
     * @param args 可选监听地址，默认仅绑定本机 TCP，生产使用 unix: 路径
     * @throws Exception 监听绑定或初始工作进程创建失败，服务启动终止
     */
    public static void main(String[] args) throws Exception {
        String address = args.length == 0 ? "tcp:127.0.0.1:9091" : args[0];
        // 所有初始进程先完成预热，随后才对后端开放任务入口
        for (int i = 0; i < POOL; i++) IDLE.add(spawn());
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    Slot slot;
                                    while ((slot = IDLE.poll()) != null)
                                        slot.process.destroyForcibly();
                                    TASKS.shutdownNow();
                                }));
        try (ServerSocketChannel server = listen(address)) {
            System.err.println("MockLab runner ready: " + address);
            while (true) {
                SocketChannel channel = server.accept();
                TASKS.submit(() -> serve(channel));
            }
        }
    }

    /**
     * 读取一个有大小上限的换行协议帧，忽略 CR 以兼容不同平台换行
     *
     * @param reader UTF-8 协议字符流
     * @return 不含换行的帧内容，未读到任何字符即结束时返回 null
     * @throws IOException 底层读取失败或协议帧超出上限
     */
    public static String readLine(BufferedReader reader) throws IOException {
        StringBuilder buffer = new StringBuilder();
        for (int ch; (ch = reader.read()) != -1; ) {
            if (ch == '\n') return buffer.toString();
            if (buffer.length() >= MAX_LINE) throw new IOException("Protocol frame too large");
            if (ch != '\r') buffer.append((char) ch);
        }
        return buffer.isEmpty() ? null : buffer.toString();
    }

    /**
     * 创建受限监听入口，开发仅允许回环地址，Unix Socket 使用组可读写权限
     *
     * @param address tcp: 地址或 unix: 套接字路径
     * @return 已绑定的服务端通道
     * @throws IOException 地址绑定失败、套接字路径已被占用或权限设置失败
     */
    private static ServerSocketChannel listen(String address) throws IOException {
        if (address.startsWith("unix:")) {
            Path socket = Path.of(address.substring(5));
            Files.createDirectories(socket.getParent());
            // 不自动删除未知文件，只有部署入口可以确认并清理遗留套接字
            if (Files.exists(socket))
                throw new IOException(
                        "Remove only the stale runner socket before startup: " + socket);
            ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            server.bind(UnixDomainSocketAddress.of(socket));
            Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-rw----"));
            return server;
        }
        String[] parts = address.substring(4).split(":");
        if (!"127.0.0.1".equals(parts[0]))
            throw new IOException("Development runner must bind loopback");
        return ServerSocketChannel.open()
                .bind(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
    }

    /**
     * 以独立 JVM 和内存上限创建工作进程，收到 READY 后才计入可用进程池
     *
     * @return 完成预热且可交互的进程槽位
     * @throws Exception 创建进程失败、预热超时或就绪握手不正确
     */
    private static Slot spawn() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process =
                new ProcessBuilder(
                                java,
                                "-Xms32m",
                                "-Xmx96m",
                                "-XX:MaxMetaspaceSize=128m",
                                "-XX:ReservedCodeCacheSize=32m",
                                "-Dpolyglot.engine.WarnInterpreterOnly=false",
                                "-cp",
                                System.getProperty("java.class.path"),
                                Worker.class.getName())
                        .redirectError(ProcessBuilder.Redirect.INHERIT)
                        .start();
        Slot slot =
                new Slot(
                        process,
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream(), StandardCharsets.UTF_8)),
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        process.getOutputStream(), StandardCharsets.UTF_8)));
        // READY 来自子进程预热完成后的专用协议输出，等待失败必须销毁未就绪进程
        Future<String> ready = TASKS.submit(() -> readLine(slot.reader));
        try {
            if (!"READY".equals(ready.get(30, TimeUnit.SECONDS)))
                throw new IOException("Worker startup failed");
        } catch (Exception error) {
            process.destroyForcibly();
            throw error;
        }
        LIVE.incrementAndGet();
        return slot;
    }

    /**
     * 处理单连接的一条任务或健康探测，连接读取也受超时约束
     *
     * @param channel 本次后端连接，方法结束时关闭
     */
    private static void serve(SocketChannel channel) {
        try (channel;
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        Channels.newInputStream(channel), StandardCharsets.UTF_8));
                BufferedWriter writer =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        Channels.newOutputStream(channel),
                                        StandardCharsets.UTF_8))) {
            Future<String> read = TASKS.submit(() -> readLine(reader));
            String line = read.get(5, TimeUnit.SECONDS);
            JsonNode command = JSON.readTree(line);
            String result =
                    "ping".equals(command.path("operation").asText())
                            ? JSON.writeValueAsString(
                                    Map.of(
                                            "ok",
                                            true,
                                            "ready",
                                            LIVE.get(),
                                            "idle",
                                            IDLE.size(),
                                            "capacity",
                                            POOL))
                            : dispatch(line);
            writer.write(result);
            writer.newLine();
            writer.flush();
        } catch (Exception error) {
            System.err.println("Runner transport closed: " + error.getClass().getSimpleName());
        }
    }

    /**
     * 在容量及等待期限内借用进程，失败任务通过退役进程阻止污染后续调用
     *
     * @param command 尚未改写的单行 JSON 任务
     * @return 带执行结果或平台错误的 JSON 响应
     * @throws Exception 排队中断或协议响应无法编码
     */
    private static String dispatch(String command) throws Exception {
        // 先限制运行与等待总量，再等待空闲进程，满载时立即拒绝而非无限排队
        if (!CAPACITY.tryAcquire()) return error("SCRIPT_CAPACITY_EXCEEDED", "脚本执行队列已满");
        Slot slot = null;
        long start = System.nanoTime();
        try {
            slot = IDLE.poll(1000, TimeUnit.MILLISECONDS);
            if (slot == null) return error("SCRIPT_CAPACITY_EXCEEDED", "等待脚本执行资源超时");
            long queued = Duration.ofNanos(System.nanoTime() - start).toMillis();
            Slot active = slot;
            Future<String> result = TASKS.submit(() -> exchange(active, command));
            try {
                String output = result.get(TIMEOUT, TimeUnit.MILLISECONDS);
                JsonNode response = JSON.readTree(output);
                Map<String, Object> map = JSON.convertValue(response, Map.class);
                map.put("queueMs", queued);
                map.put(
                        "scriptMs",
                        Duration.ofNanos(System.nanoTime() - start).toMillis() - queued);
                return JSON.writeValueAsString(map);
            } catch (TimeoutException timeout) {
                // 超时和进程异常均不能把槽位重新交给其他请求
                retire(slot);
                slot = null;
                return error("SCRIPT_TIMEOUT", "脚本执行超时");
            } catch (Exception failure) {
                // 超时和进程异常均不能把槽位重新交给其他请求
                retire(slot);
                slot = null;
                return error("SCRIPT_EXECUTION_ERROR", "脚本执行进程异常退出");
            }
        } finally {
            // 失败路径已将槽位置空，仅将仍有效的进程归还队列，并始终释放容量额度
            if (slot != null) IDLE.offer(slot);
            CAPACITY.release();
        }
    }

    /**
     * 在独占进程槽位内完成一次输入输出交互，不与其他任务共享工作进程
     *
     * @param slot 当前任务借用的工作进程
     * @param command 单行 JSON 任务
     * @return 工作进程返回的单行 JSON 响应
     * @throws IOException 进程退出、读写失败或响应超出协议上限
     */
    private static String exchange(Slot slot, String command) throws IOException {
        slot.writer.write(command);
        slot.writer.newLine();
        slot.writer.flush();
        String line = readLine(slot.reader);
        if (line == null) throw new IOException("Worker terminated");
        return line;
    }

    /**
     * 销毁失效进程并异步尝试补充，正在退役的槽位不会再次归还空闲队列
     *
     * @param slot 超时或异常退出的进程槽位
     */
    private static void retire(Slot slot) {
        slot.process.destroyForcibly();
        LIVE.decrementAndGet();
        TASKS.submit(
                () -> {
                    // 沿用已有的有限补充次数，补充失败会降低就绪进程数并反映到健康探测
                    for (int attempt = 0; attempt < 3; attempt++) {
                        try {
                            IDLE.put(spawn());
                            return;
                        } catch (Exception error) {
                            System.err.println(
                                    "Unable to replenish worker: "
                                            + error.getClass().getSimpleName());
                        }
                    }
                });
    }

    /**
     * 生成监督服务拒绝任务或终止任务时的协议错误，交由后端映射 HTTP 状态
     *
     * @param code 稳定的平台错误码
     * @param message 可公开的失败说明
     * @return 标准协议错误 JSON
     * @throws Exception JSON 编码失败
     */
    private static String error(String code, String message) throws Exception {
        return JSON.writeValueAsString(Map.of("ok", false, "errorCode", code, "message", message));
    }

    /**
     * 绑定一个工作进程及其专属协议流，借出期间仅由一个任务访问
     *
     * @param process 受内存上限约束的子进程
     * @param reader 读取子进程标准输出的 UTF-8 字符流
     * @param writer 写入子进程标准输入的 UTF-8 字符流
     */
    private record Slot(Process process, BufferedReader reader, BufferedWriter writer) {}
}
