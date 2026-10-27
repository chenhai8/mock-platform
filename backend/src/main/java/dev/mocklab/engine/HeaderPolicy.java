package dev.mocklab.engine;

import dev.mocklab.common.BusinessException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 统一校验和合并响应头，阻止传输层头覆盖及控制字符注入
 */
@Component
public class HeaderPolicy {
    private static final int MAX_HEADERS = 32;
    private static final int MAX_BYTES = 16384;
    private static final Set<String> RESERVED =
            Set.of(
                    "host",
                    "content-length",
                    "transfer-encoding",
                    "connection",
                    "upgrade",
                    "keep-alive",
                    "trailer",
                    "te",
                    "x-mock-platform-error");

    /**
     * 以默认 Content-Type 为基础合并配置头和脚本头，脚本响应头优先覆盖配置
     *
     * @param contentType 配置的默认媒体类型
     * @param configured 管理端保存的响应头
     * @param scripted 脚本返回的响应头，可覆盖 Content-Type
     * @return 名称统一为小写且通过限制校验的响应头
     */
    public Map<String, String> merge(
            String contentType, Map<String, String> configured, Map<String, String> scripted) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", contentType);
        append(headers, configured, false);
        // 覆盖顺序为默认媒体类型、配置头、脚本头，最终统一检查数量与字节上限
        append(headers, scripted, true);
        assertLimits(headers);
        return headers;
    }

    /**
     * 逐项校验后追加响应头，禁止控制字符、重复名称及传输层保留头
     *
     * @param target 待写入的最终响应头
     * @param input 本次合并的响应头集合
     * @param allowContentType 仅脚本头允许覆盖默认 Content-Type
     */
    private void append(
            Map<String, String> target, Map<String, String> input, boolean allowContentType) {
        if (input == null) throw new BusinessException(400, "INVALID_CONFIG", "响应头不能为空");
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String name = entry.getKey().toLowerCase(Locale.ROOT), value = entry.getValue();
            if (!name.matches("[!#$%&'*+.^_`|~0-9a-z-]+")
                    || value == null
                    || value.chars().anyMatch(c -> c < 32 && c != 9 || c == 127)
                    || RESERVED.contains(name)
                    || !seen.add(name)
                    || name.equals("content-type") && !allowContentType)
                throw new BusinessException(400, "INVALID_CONFIG", "响应头名称、值或重复项不合法：" + name);
            target.put(name, value);
        }
    }

    /**
     * 检查合并后的总大小和媒体类型，避免分别合规的响应头叠加后超限
     *
     * @param headers 已经合并的最终响应头
     */
    private void assertLimits(Map<String, String> headers) {
        int bytes =
                headers.entrySet().stream()
                        .mapToInt(
                                e ->
                                        (e.getKey() + e.getValue())
                                                .getBytes(StandardCharsets.UTF_8)
                                                .length)
                        .sum();
        if (headers.size() > MAX_HEADERS || bytes > MAX_BYTES)
            throw new BusinessException(400, "INVALID_CONFIG", "响应头超过数量或大小限制");
        try {
            MediaType type = MediaType.parseMediaType(headers.get("content-type"));
            if (type.getCharset() != null
                    && !type.getCharset().equals(StandardCharsets.UTF_8))
                throw new IllegalArgumentException("Only UTF-8");
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "INVALID_CONFIG", "Content-Type 不合法或不是 UTF-8");
        }
    }
}
