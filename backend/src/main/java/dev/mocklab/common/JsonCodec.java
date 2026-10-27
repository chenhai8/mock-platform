package dev.mocklab.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * 集中处理管理配置与脚本协议的 JSON 转换，并转换为平台业务异常
 */
@Component
@RequiredArgsConstructor
public class JsonCodec {
    private final ObjectMapper mapper;

    /**
     * 将配置或协议对象转换为 JSON，无法编码时保持统一业务错误形式
     *
     * @param value 需要序列化的配置或数据对象
     * @return JSON 文本
     * @throws BusinessException 数据无法序列化
     */
    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(400, "INVALID_CONFIG", "数据无法序列化");
        }
    }

    /**
     * 按目标类型解析 JSON，并遵循平台已有 ObjectMapper 校验设置
     *
     * @param <T> 目标对象类型
     * @param json 原始 JSON 文本
     * @param type 目标类型
     * @return 解析后的目标对象
     * @throws BusinessException JSON 内容或字段不符合目标类型
     */
    public <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(400, "INVALID_REQUEST", "JSON 格式或字段不正确");
        }
    }
}
