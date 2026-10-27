package dev.mocklab.engine;

import dev.mocklab.common.BusinessException;
import dev.mocklab.common.JsonCodec;
import dev.mocklab.model.MockResult;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成平台错误响应，区分管理调试详情与真实 Mock 调用可见信息
 */
@Component
@RequiredArgsConstructor
public class ResultFactory {
    private final JsonCodec json;

    /**
     * 生成平台错误报文及专用标识头，真实调用不暴露脚本内部错误详情
     *
     * @param error 待转换的业务异常
     * @param traceId 本次调用追踪编号
     * @param diagnostics 是否为可展示完整错误提示的管理调试
     * @return 可直接转换为 HTTP 输出的错误结果
     */
    public MockResult error(BusinessException error, String traceId, boolean diagnostics) {
        MockResult result = new MockResult();
        result.setStatus(error.getStatus());
        result.setErrorCode(error.getCode());
        Map<String, String> headers = new LinkedHashMap<>(error.getHeaders());
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("X-Mock-Platform-Error", error.getCode());
        result.setHeaders(headers);
        String message = diagnostics ? error.getMessage() : publicMessage(error);
        result.setBody(
                json.write(
                        Map.of(
                                "error",
                                Map.of(
                                        "code",
                                        error.getCode(),
                                        "message",
                                        message,
                                        "traceId",
                                        traceId))));
        return result;
    }

    /**
     * 隐藏真实调用中的脚本内部错误，同时保留容量不足和超时的可操作提示
     *
     * @param error 平台业务异常
     * @return 允许真实调用方看到的错误说明
     */
    private String publicMessage(BusinessException error) {
        if (error.getStatus() >= 500 && error.getStatus() != 504 && error.getStatus() != 503)
            return "Mock 执行失败，请通过追踪编号检查调用记录";
        return error.getMessage();
    }
}
