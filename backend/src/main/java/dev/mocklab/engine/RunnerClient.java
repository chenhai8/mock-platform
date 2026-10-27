package dev.mocklab.engine;

import dev.mocklab.common.BusinessException;
import dev.mocklab.common.JsonCodec;
import dev.mocklab.config.MockProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 通过开发用回环 TCP 或生产用 Unix Socket 调用独立脚本服务
 */
@Component
@RequiredArgsConstructor
public class RunnerClient {
    private static final int FRAME_LIMIT = 5 * 1024 * 1024;
    private static final ExecutorService IO = Executors.newVirtualThreadPerTaskExecutor();
    private final MockProperties properties;
    private final JsonCodec json;

    /**
     * 在连接和读写时间限制内发送一条协议命令，结束时关闭本次连接
     *
     * @param command JSON 协议命令，包含 operation 等字段
     * @return runner 的完整协议响应
     */
    public Map<String, Object> invoke(Map<String, Object> command) {
        try (SocketChannel socket = open()) {
            return IO.submit(() -> exchange(socket, command)).get(5, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new BusinessException(503, "MOCK_SERVICE_UNAVAILABLE", "脚本执行服务不可用");
        }
    }

    /**
     * 执行或校验脚本，并将 runner 错误码映射为平台 HTTP 状态
     *
     * @param operation run 执行脚本，validate 仅校验语法
     * @param code JavaScript 源码
     * @param request 传给脚本的请求上下文，校验时可为空映射
     * @return 执行成功的完整协议响应
     */
    public Map<String, Object> run(String operation, String code, Object request) {
        Map<String, Object> response =
                invoke(Map.of("operation", operation, "code", code, "request", request));
        if (response.get("ok") instanceof Boolean success && success) return response;
        String error = String.valueOf(response.getOrDefault("errorCode", "SCRIPT_EXECUTION_ERROR"));
        int status =
                "SCRIPT_TIMEOUT".equals(error)
                        ? 504
                        : "SCRIPT_CAPACITY_EXCEEDED".equals(error) ? 503 : 500;
        throw new BusinessException(
                status, error, String.valueOf(response.getOrDefault("message", "脚本执行失败")));
    }

    /**
     * 根据部署模式连接脚本服务，TCP 连接额外设置建立连接超时
     *
     * @return 由上层调用负责关闭的已连接通道
     * @throws Exception 连接地址无效、建立连接失败或超时
     */
    private SocketChannel open() throws Exception {
        String address = properties.getRunnerAddress();
        if (address.startsWith("unix:")) {
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            channel.connect(UnixDomainSocketAddress.of(address.substring(5)));
            return channel;
        }
        String[] parts = address.substring(4).split(":");
        SocketChannel channel = SocketChannel.open();
        channel.socket().connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), 1000);
        return channel;
    }

    /**
     * 以换行分帧发送 JSON 并有界读取响应，避免脚本正文被当作协议边界
     *
     * @param socket 当前任务的独立连接
     * @param command 待发送协议命令
     * @return 解码后的响应映射
     * @throws Exception 协议读写失败、响应超限或 JSON 无法解析
     */
    private Map<String, Object> exchange(SocketChannel socket, Map<String, Object> command)
            throws Exception {
        BufferedWriter writer =
                new BufferedWriter(
                        new OutputStreamWriter(
                                Channels.newOutputStream(socket), StandardCharsets.UTF_8));
        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                Channels.newInputStream(socket), StandardCharsets.UTF_8));
        writer.write(json.write(command));
        writer.newLine();
        writer.flush();
        StringBuilder response = new StringBuilder();
        for (int value; (value = reader.read()) != -1 && value != '\n'; ) {
            if (response.length() >= FRAME_LIMIT)
                throw new BusinessException(500, "RESPONSE_TOO_LARGE", "脚本响应超过协议限制");
            response.append((char) value);
        }
        return json.read(response.toString(), Map.class);
    }
}
