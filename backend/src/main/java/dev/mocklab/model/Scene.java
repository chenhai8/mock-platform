package dev.mocklab.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 接口的一条条件响应规则，默认场景负责所有未命中条件的请求
 */
@Data
public class Scene {
    /**
     * 接口内唯一且稳定的场景标识
     */
    private String id;
    /**
     * 场景展示名称，同时写入调用记录
     */
    private String name;

    /**
     * 是否为兜底场景，对外 JSON 字段名为 default，默认场景不能停用
     */
    @JsonProperty("default")
    private boolean fallback;

    /**
     * 是否参与场景匹配
     */
    private boolean enabled = true;
    /**
     * 非默认场景优先级，取值 1～9999，数值越大越先匹配且不能重复
     */
    private int priority;
    /**
     * 条件字段路径，根节点限定为 body、query、headers 或 pathParams
     */
    private String field = "body.orderId";
    /**
     * 条件运算符，支持 eq、ne、contains、exists、missing
     */
    private String operator = "eq";
    /**
     * 与标量字符串表示比较的目标值，存在性判断不使用此值
     */
    private String value = "";
    /**
     * 响应模式，raw 返回原样正文，script 执行 JavaScript
     */
    private String mode = "raw";
    /**
     * 配置 HTTP 状态码，脚本未返回 status 时使用此值
     */
    private int status = 200;
    /**
     * 默认响应媒体类型，正文仅支持 UTF-8
     */
    private String contentType = "application/json; charset=utf-8";
    /**
     * 自定义响应头，禁止覆盖传输层保留头及重复配置 Content-Type
     */
    private Map<String, String> headers = new LinkedHashMap<>();
    /**
     * 响应生成后的等待时长，单位为毫秒，不占用脚本进程
     */
    private int delay;
    /**
     * raw 模式的原样正文或 script 模式的 JavaScript 源码
     */
    private String code = "";
}
