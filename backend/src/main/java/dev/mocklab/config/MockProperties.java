package dev.mocklab.config;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 MockLab 容量限制、runner 地址及日志保留设置，字节限制按 UTF-8 计算
 */
@Data
@ConfigurationProperties("mocklab")
public class MockProperties {
    /**
     * 脚本服务地址，开发使用 tcp:127.0.0.1:9091，Linux 容器使用 unix: 路径
     */
    private String runnerAddress = "tcp:127.0.0.1:9091";
    /**
     * 展示给管理端的脚本超时配置，单位为毫秒，强制终止以 runner 环境变量为准
     */
    private int scriptTimeoutMs = 1000;
    /**
     * 请求正文上限，单位为 UTF-8 字节
     */
    private int requestLimit = 1048576;
    /**
     * 最终响应正文上限，单位为 UTF-8 字节
     */
    private int responseLimit = 2097152;
    /**
     * 单份脚本源码上限，单位为 UTF-8 字节
     */
    private int scriptLimit = 65536;
    /**
     * 场景允许配置的最大额外延迟，单位为毫秒
     */
    private int delayLimitMs = 10000;
    /**
     * 同时等待响应延迟的最大调用数，满载时拒绝新的延迟任务
     */
    private int delayCapacity = 200;
    /**
     * 调用日志保留天数，过期数据按固定批次清理
     */
    private int logDays = 7;
    /**
     * 调用日志数量上限，超量数据按最早主键分批清理
     */
    private long logMaxRows = 1000000;
    /**
     * 是否记录脱敏后的请求与响应详情，默认关闭
     */
    private boolean logBody;
    /**
     * 单份诊断详情的最大 UTF-8 字节数，超出后截断
     */
    private int logBodyLimit = 4096;
    /**
     * 待落库调用日志队列容量，满载时计数并丢弃新日志，不阻塞 Mock 调用
     */
    private int logQueueCapacity = 10000;
}
