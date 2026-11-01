package dev.mocklab.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 调用摘要及可选脱敏详情，允许系统或接口尚未匹配时记录失败调用
 */
@Data
@TableName("mock_call_log")
public class MockCallLog {
    /**
     * 调用记录数据库主键
     */
    private Long id;
    /**
     * 关联调用响应与服务日志的追踪编号
     */
    private String traceId;
    /**
     * 匹配到的系统主键，系统未匹配时为空
     */
    private Long systemId;
    /**
     * 匹配到的接口主键，接口未匹配时为空
     */
    private Long endpointId;
    /**
     * 调用时的系统名称快照
     */
    private String systemName;
    /**
     * 调用来源：LIVE、DRAFT_DEBUG 或 PUBLISHED_DEBUG
     */
    private String source;
    /**
     * 请求 HTTP 方法
     */
    private String requestMethod;
    /**
     * 匹配的配置路径模板，未匹配时记录占位信息
     */
    private String requestPath;
    /**
     * 调用使用的发布版本号或草稿修订号
     */
    private Long configVersion;
    /**
     * 命中的场景名称
     */
    private String scene;
    /**
     * 最终 HTTP 状态码
     */
    private Integer status;
    /**
     * 平台错误码，正常场景响应为空
     */
    private String errorCode;
    /**
     * 平台处理总时长，单位为毫秒
     */
    private Long durationMs;
    /**
     * 等待脚本进程时长，单位为毫秒
     */
    private Long queueMs;
    /**
     * 脚本执行时长，单位为毫秒
     */
    private Long scriptMs;
    /**
     * 配置响应延迟，单位为毫秒
     */
    private Integer delayMs;
    /**
     * 原始请求正文的 UTF-8 字节数
     */
    private Integer requestBytes;
    /**
     * 生成响应正文的 UTF-8 字节数，HEAD 抑制输出前统计
     */
    private Integer responseBytes;
    /**
     * 后端观察到的来源地址，经过网关时可能为代理地址
     */
    private String sourceAddress;
    /**
     * 开启诊断后记录的脱敏请求摘要，按字节限制截断
     */
    private String requestDetail;
    /**
     * 开启诊断后记录的脱敏 JSON 响应，非 JSON 正文不记录
     */
    private String responseDetail;
    /**
     * 日志组装时间，固定毫秒精度的 UTC 字符串
     */
    private String createdAt;
}
