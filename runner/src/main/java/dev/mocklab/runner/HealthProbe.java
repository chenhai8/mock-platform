package dev.mocklab.runner;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * 供 Docker 健康检查使用的 Unix Socket 探针，只检查工作进程就绪状态
 */
public final class HealthProbe {
    /**
     * 向 Unix Socket 发送 ping 并确认至少一个进程就绪，失败时以非零退出供容器判定异常
     *
     * @param args 第一个参数为 runner 的 Unix Socket 路径
     * @throws Exception 连接失败、未收到响应或进程池尚未就绪
     */
    public static void main(String[] args) throws Exception {
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(args[0]));
            channel.write(StandardCharsets.UTF_8.encode("{\"operation\":\"ping\"}\n"));
            ByteBuffer response = ByteBuffer.allocate(1024);
            int length = channel.read(response);
            if (length <= 0) throw new IllegalStateException("Runner is not ready");
            String text = new String(response.array(), 0, length, StandardCharsets.UTF_8);
            if (!text.contains("\"ok\":true") || text.contains("\"ready\":0"))
                throw new IllegalStateException("No script worker available");
        }
    }
}
