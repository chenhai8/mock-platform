package dev.mocklab.service;

import dev.mocklab.model.Commands;
import dev.mocklab.model.MockResult;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;

/**
 * 统一编排真实 Mock 请求与管理调试的路由、响应及日志记录
 */
public interface DispatchService {
    /**
     * 分发真实 HTTP 请求并按配置响应
     *
     * @param request HTTP 请求
     * @return 含原样正文的异步 HTTP 响应
     */
    CompletableFuture<ResponseEntity<byte[]>> dispatch(HttpServletRequest request);

    /**
     * 使用当前编辑内容或服务器发布快照执行调试
     *
     * @param id 接口主键
     * @param command 调试输入
     * @param address 来源地址
     * @return 调试结果，含状态和匹配信息
     */
    CompletableFuture<MockResult> debug(Long id, Commands.Debug command, String address);
}
