package dev.mocklab.config;

import dev.mocklab.common.JsonCodec;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * 限制管理写请求的来源标记和报文大小，避免跨站表单提交及无界读取
 */
@Component
@RequiredArgsConstructor
public class ManagementRequestFilter extends OncePerRequestFilter {
    private static final int MANAGEMENT_LIMIT = 5 * 1024 * 1024;
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final JsonCodec json;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")
                || !WRITES.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        // 自定义头用于阻止普通跨站表单提交，不是身份认证，管理入口仍依赖内网访问控制
        if (!"1".equals(request.getHeader("X-MockLab-Request"))) {
            reject(response, 403, "请从管理页面提交请求");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MANAGEMENT_LIMIT + 1);
        if (body.length > MANAGEMENT_LIMIT) {
            reject(response, 413, "配置或调试输入超过 5 MiB");
            return;
        }
        // 过滤器已消耗原始流，必须用缓存请求包装后再交给参数绑定
        chain.doFilter(new BufferedRequest(request, body), response);
    }

    /**
     * 在进入 Controller 前输出统一错误结构，不继续执行后续过滤链
     *
     * @param response 当前 HTTP 响应
     * @param status 拒绝请求的 HTTP 状态码
     * @param message 可公开的拒绝原因
     * @throws IOException 响应正文无法写入
     */
    private void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter()
                .write(
                        json.write(
                                Map.of(
                                        "error",
                                        Map.of("code", "INVALID_REQUEST", "message", message))));
    }

    /**
     * 在有界读取校验后向下游提供缓存正文，避免过滤器先读流导致 Controller 收到空正文
     */
    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] bytes;

        /**
         * 复用原请求元数据，并持有已经完成大小校验的正文副本
         *
         * @param request 原始管理请求
         * @param bytes 有界读取后的请求正文
         */
        BufferedRequest(HttpServletRequest request, byte[] bytes) {
            super(request);
            this.bytes = bytes;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async input is not supported");
                }
            };
        }
    }
}
