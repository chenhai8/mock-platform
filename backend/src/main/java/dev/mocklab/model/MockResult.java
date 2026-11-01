package dev.mocklab.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mock 执行结果及诊断信息，真实 HTTP 调用只输出其中的状态、响应头和正文
 */
@Data
public class MockResult {
    /**
     * 最终 HTTP 状态码
     */
    private int status = 200;
    /**
     * 校验并合并后的最终响应头
     */
    private Map<String, String> headers = new LinkedHashMap<>();
    /**
     * 最终 UTF-8 响应正文，字符串原样输出，JSON 对象在 runner 中完成序列化
     */
    private String body = "";
    /**
     * 命中的场景名称，未生成响应时可为空
     */
    private String scene;
    /**
     * 平台生成的调用追踪编号，用于关联调试结果和日志
     */
    private String traceId;
    /**
     * 调用来源：LIVE、DRAFT_DEBUG 或 PUBLISHED_DEBUG
     */
    private String source;
    /**
     * 平台执行错误码，正常 Mock 响应可为空
     */
    private String errorCode;
    /**
     * 真实及发布调试使用发布版本，草稿调试使用页面修订号
     */
    private long configVersion;
    /**
     * 包含排队、执行和响应延迟的处理时长，单位为毫秒
     */
    private long durationMs;
    /**
     * 等待空闲脚本工作进程的时长，单位为毫秒
     */
    private long queueMs;
    /**
     * 脚本任务交互执行时长，单位为毫秒
     */
    private long scriptMs;
    /**
     * 配置的额外响应延迟，单位为毫秒
     */
    private int delayMs;
}
