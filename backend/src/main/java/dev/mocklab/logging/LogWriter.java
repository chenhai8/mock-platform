package dev.mocklab.logging;

import dev.mocklab.common.DbTime;
import dev.mocklab.common.JsonCodec;
import dev.mocklab.config.MockProperties;
import dev.mocklab.entity.MockCallLog;
import dev.mocklab.model.Exchange;
import dev.mocklab.model.MockResult;
import dev.mocklab.service.CallLogService;

import io.micrometer.core.instrument.MeterRegistry;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通过有界队列异步批量记录调用，日志故障不影响已经生成的 Mock 响应
 */
@Component
@Slf4j
public class LogWriter {
    private static final int BATCH_SIZE = 500;
    private final CallLogService logs;
    private final JsonCodec json;
    private final MockProperties properties;
    private final BlockingQueue<MockCallLog> queue;
    private final AtomicLong dropped = new AtomicLong();

    /**
     * 初始化有界日志队列并注册队列长度和丢弃数量指标
     *
     * @param logs 日志持久化领域服务
     * @param json 平台 JSON 转换器
     * @param properties 日志容量、诊断和保留设置
     * @param registry 应用指标注册器
     */
    public LogWriter(
            CallLogService logs,
            JsonCodec json,
            MockProperties properties,
            MeterRegistry registry) {
        this.logs = logs;
        this.json = json;
        this.properties = properties;
        queue = new ArrayBlockingQueue<>(properties.getLogQueueCapacity());
        registry.gauge("mocklab.logs.queue", queue, BlockingQueue::size);
        registry.gauge("mocklab.logs.dropped", dropped);
    }

    /**
     * 组装摘要并尝试入队，队列满或记录失败只更新丢弃指标，不阻塞业务响应
     *
     * @param systemId 匹配系统主键，可为空
     * @param endpointId 匹配接口主键，可为空
     * @param systemName 调用时系统名称，可为空
     * @param template 配置路径模板或未匹配占位值
     * @param address 后端观察到的来源地址
     * @param request 用于记录摘要和可选诊断的请求
     * @param result 已生成且补齐追踪信息的响应
     */
    public void record(
            Long systemId,
            Long endpointId,
            String systemName,
            String template,
            String address,
            Exchange request,
            MockResult result) {
        try {
            MockCallLog entry = assemble(result);
            entry.setSystemId(systemId);
            entry.setEndpointId(endpointId);
            entry.setSystemName(systemName);
            entry.setRequestPath(template);
            entry.setSourceAddress(address);
            entry.setRequestMethod(request.getMethod());
            entry.setRequestBytes(request.getRawBody().getBytes(StandardCharsets.UTF_8).length);
            if (properties.isLogBody()) attachDiagnostics(entry, request, result);
            // 日志队列满时丢弃并计数，不能让诊断记录反向阻塞 Mock 响应
            if (!queue.offer(entry)) {
                dropped.incrementAndGet();
                log.warn("Mock call log queue full");
            }
        } catch (Exception error) {
            dropped.incrementAndGet();
            log.warn("Unable to enqueue mock call summary", error);
        }
    }

    /**
     * 按固定批次落库，失败批次计为丢弃而不重试，避免日志故障拖住 Mock 主流程
     */
    @Scheduled(fixedDelay = 500)
    public synchronized void flush() {
        List<MockCallLog> batch = new ArrayList<>();
        queue.drainTo(batch, BATCH_SIZE);
        if (CollectionUtils.isEmpty(batch)) return;
        try {
            logs.saveBatch(batch, BATCH_SIZE);
        } catch (Exception error) {
            dropped.addAndGet(batch.size());
            log.error("Unable to persist mock call summaries", error);
        }
    }

    /**
     * 定期执行有限批次的日志清理，清理失败仅记录平台错误
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanup() {
        try {
            logs.prune();
        } catch (Exception error) {
            log.error("Unable to clean mock call summaries", error);
        }
    }

    /**
     * 关闭前尝试刷新一个批次，不能保证队列在异常停机时完整落库
     */
    @PreDestroy
    public void shutdown() {
        flush();
    }

    /**
     * 从执行结果提取调用摘要，响应字节数在 HEAD 抑制正文之前统计
     *
     * @param result 包含追踪号、来源及耗时的执行结果
     * @return 尚未补齐系统和请求信息的日志实体
     */
    private MockCallLog assemble(MockResult result) {
        MockCallLog entry = new MockCallLog();
        entry.setTraceId(result.getTraceId());
        entry.setStatus(result.getStatus());
        entry.setSource(result.getSource());
        entry.setConfigVersion(result.getConfigVersion());
        entry.setScene(result.getScene());
        entry.setErrorCode(result.getErrorCode());
        entry.setDurationMs(result.getDurationMs());
        entry.setQueueMs(result.getQueueMs());
        entry.setScriptMs(result.getScriptMs());
        entry.setDelayMs(result.getDelayMs());
        entry.setResponseBytes(result.getBody().getBytes(StandardCharsets.UTF_8).length);
        entry.setCreatedAt(DbTime.now());
        return entry;
    }

    /**
     * 仅在诊断开关开启时记录脱敏结构，非 JSON 响应使用占位说明
     *
     * @param entry 待补齐诊断字段的调用日志
     * @param request 解析后的请求上下文
     * @param result 最终响应
     */
    private void attachDiagnostics(MockCallLog entry, Exchange request, MockResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("headers", redact(request.getHeaders()));
        detail.put("query", redact(request.getQuery()));
        detail.put("body", redact(request.getBody()));
        entry.setRequestDetail(limit(json.write(detail)));
        try {
            entry.setResponseDetail(
                    limit(json.write(redact(json.read(result.getBody(), Object.class)))));
        } catch (Exception unsupported) {
            // 非 JSON 内容无法按字段脱敏，因此不回退为记录原始正文
            entry.setResponseDetail("[未记录非 JSON 正文]");
        }
    }

    /**
     * 递归复制对象和数组，对敏感字段的值整体替换，避免修改原始请求数据
     *
     * @param value 待脱敏的 JSON 数据
     * @return 脱敏后的结构或原始非敏感标量
     */
    private Object redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> clean = new LinkedHashMap<>();
            map.forEach(
                    (key, item) ->
                            clean.put(
                                    String.valueOf(key),
                                    sensitive(String.valueOf(key)) ? "[REDACTED]" : redact(item)));
            return clean;
        }
        if (value instanceof List<?> list) return list.stream().map(this::redact).toList();
        return value;
    }

    /**
     * 按字段名识别认证和凭证类数据，避免诊断详情记录常见密钥
     *
     * @param key JSON 字段名或请求头名称
     * @return 是否需要隐藏整个字段值
     */
    private boolean sensitive(String key) {
        return key.toLowerCase()
                .matches(
                        ".*(authorization|cookie|password|passwd|pwd|token|secret|credential|api[-_]?key).*");
    }

    /**
     * 按 UTF-8 字节限制诊断正文，并附加可见截断标记
     *
     * @param value 已经脱敏并序列化的诊断文本
     * @return 原文本或带截断标记的文本
     */
    private String limit(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= properties.getLogBodyLimit()) return value;
        return new String(bytes, 0, properties.getLogBodyLimit(), StandardCharsets.UTF_8)
                + " [已截断]";
    }
}
