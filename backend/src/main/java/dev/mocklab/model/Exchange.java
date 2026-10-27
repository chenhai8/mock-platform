package dev.mocklab.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脚本与条件匹配共用的请求上下文，保留原始正文及解析后的结构
 */
@Data
public class Exchange {
    /**
     * 请求 HTTP 方法，真实请求和调试均使用大写方法名
     */
    private String method;
    /**
     * 去除 /m/{systemKey} 前缀后的接口路径，保留大小写与尾斜杠
     */
    private String path;
    /**
     * 未经表单解码的原始查询字符串，不包含问号
     */
    private String queryString;
    /**
     * 按接口路径模板提取的参数，参数值仅解码一次
     */
    private Map<String, Object> pathParams = new LinkedHashMap<>();
    /**
     * 已解码的查询参数，单值为字符串，同名多值为列表
     */
    private Map<String, Object> query = new LinkedHashMap<>();
    /**
     * 请求头名称统一为小写，同名多值保留为列表
     */
    private Map<String, Object> headers = new LinkedHashMap<>();
    /**
     * JSON 或表单的解析结果，文本、XML 与空正文为 null
     */
    private Object body;
    /**
     * 经过严格 UTF-8 解码的原始正文，文本及 XML 脚本应读取此字段
     */
    private String rawBody = "";
}
