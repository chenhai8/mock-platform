package dev.mocklab.web;

import dev.mocklab.service.DispatchService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * 接收所有动态 Mock 路径，由调度服务按已发布配置生成 HTTP 响应
 */
@RestController
@RequiredArgsConstructor
public class MockController {
    private final DispatchService dispatch;

    /**
     * 统一接收动态 Mock 路径，避免为用户配置的每个地址创建 Controller 方法
     *
     * @param request 包含系统命名空间和实际报文的 HTTP 请求
     * @return 按发布配置生成的异步 HTTP 响应
     */
    @RequestMapping(
            value = {"/m/**", "/m"},
            method = {
                RequestMethod.GET,
                RequestMethod.POST,
                RequestMethod.PUT,
                RequestMethod.PATCH,
                RequestMethod.DELETE,
                RequestMethod.HEAD,
                RequestMethod.OPTIONS,
                RequestMethod.TRACE
            })
    public CompletableFuture<ResponseEntity<byte[]>> dispatch(HttpServletRequest request) {
        return dispatch.dispatch(request);
    }
}
