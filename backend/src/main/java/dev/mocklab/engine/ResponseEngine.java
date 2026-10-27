package dev.mocklab.engine;

import dev.mocklab.common.BusinessException;
import dev.mocklab.config.MockProperties;
import dev.mocklab.model.Exchange;
import dev.mocklab.model.MockResult;
import dev.mocklab.model.Scene;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 按命中场景生成原样或脚本响应，并校验最终 HTTP 响应契约
 */
@Component
@RequiredArgsConstructor
public class ResponseEngine {
    private final HeaderPolicy headerPolicy;
    private final RunnerClient runner;
    private final MockProperties properties;

    /**
     * 按命中场景生成响应，脚本覆盖字段后仍执行最终大小和状态约束
     *
     * @param scene 已经命中的响应场景
     * @param request 已经解析并填充路径参数的请求上下文
     * @return 尚未等待配置延迟的执行结果
     */
    public MockResult execute(Scene scene, Exchange request) {
        MockResult result = new MockResult();
        result.setStatus(scene.getStatus());
        result.setScene(scene.getName());
        result.setDelayMs(scene.getDelay());
        if ("raw".equals(scene.getMode())) {
            result.setHeaders(
                    headerPolicy.merge(scene.getContentType(), scene.getHeaders(), Map.of()));
            result.setBody(scene.getCode());
        } else fillScript(result, scene, request);
        assertBody(result);
        return result;
    }

    /**
     * 将工作进程响应填入结果，脚本未指定状态时回退场景配置并保留执行耗时
     *
     * @param result 待填充响应
     * @param scene 脚本及默认响应配置
     * @param request 传给 main 的请求上下文
     */
    private void fillScript(MockResult result, Scene scene, Exchange request) {
        Map<String, Object> executed = runner.run("run", scene.getCode(), request);
        Map<String, Object> value = (Map<String, Object>) executed.get("result");
        result.setStatus(
                value.get("status") instanceof Number number
                        ? number.intValue()
                        : scene.getStatus());
        result.setBody((String) value.get("body"));
        try {
            result.setHeaders(
                    headerPolicy.merge(
                            scene.getContentType(),
                            scene.getHeaders(),
                            (Map<String, String>) value.getOrDefault("headers", Map.of())));
        } catch (BusinessException error) {
            throw new BusinessException(500, "INVALID_SCRIPT_RESPONSE", error.getMessage());
        }
        result.setQueueMs(((Number) executed.getOrDefault("queueMs", 0)).longValue());
        result.setScriptMs(((Number) executed.getOrDefault("scriptMs", 0)).longValue());
    }

    /**
     * 检查最终响应，脚本不能绕过状态码、正文大小及 204/304 无正文约束
     *
     * @param result 原样或脚本生成的完整响应
     */
    private void assertBody(MockResult result) {
        if (result.getStatus() < 200 || result.getStatus() > 599)
            throw new BusinessException(500, "INVALID_SCRIPT_RESPONSE", "脚本返回了无效状态码");
        if (result.getBody() == null)
            throw new BusinessException(500, "INVALID_SCRIPT_RESPONSE", "响应正文缺失");
        if (result.getBody().getBytes(StandardCharsets.UTF_8).length
                > properties.getResponseLimit())
            throw new BusinessException(500, "RESPONSE_TOO_LARGE", "响应正文超过限制");
        if ((result.getStatus() == 204 || result.getStatus() == 304)
                && result.getBody().length() > 0)
            throw new BusinessException(500, "INVALID_SCRIPT_RESPONSE", "204/304 响应不能包含正文");
    }
}
