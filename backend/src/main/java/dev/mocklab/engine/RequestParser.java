package dev.mocklab.engine;

import dev.mocklab.common.BusinessException;
import dev.mocklab.common.JsonCodec;
import dev.mocklab.config.MockProperties;
import dev.mocklab.model.Exchange;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 将真实 HTTP 请求与调试输入转换为同一脚本上下文，严格限制正文大小及编码
 */
@Component
@RequiredArgsConstructor
public class RequestParser {
    private final MockProperties properties;
    private final JsonCodec json;

    /**
     * 有界读取真实请求正文并收集多值请求头，统一交给解析入口处理
     *
     * @param request Servlet 原始请求
     * @param path 去除系统前缀的请求路径
     * @return 可用于条件匹配及脚本执行的请求上下文
     */
    public Exchange fromServlet(HttpServletRequest request, String path) {
        try {
            // 多读取一个字节即可识别超限，不先把整个请求正文载入内存
            byte[] bytes = request.getInputStream().readNBytes(properties.getRequestLimit() + 1);
            Map<String, Object> headers = new LinkedHashMap<>();
            Collections.list(request.getHeaderNames())
                    .forEach(
                            name ->
                                    headers.put(
                                            name.toLowerCase(Locale.ROOT),
                                            collapse(Collections.list(request.getHeaders(name)))));
            return parse(request.getMethod(), path, request.getQueryString(), headers, bytes);
        } catch (IOException ex) {
            throw new BusinessException(400, "INVALID_REQUEST", "无法读取请求正文");
        }
    }

    /**
     * 统一解析真实请求与调试输入，保留原始文本并按 Content-Type 提取结构化正文
     *
     * @param method 请求方法
     * @param path 系统内请求路径
     * @param query 原始查询字符串，可为空
     * @param headers 请求头集合
     * @param bytes 未经解码的 UTF-8 正文字节
     * @return 包含原始和结构化数据的请求上下文
     */
    public Exchange parse(
            String method, String path, String query, Map<String, Object> headers, byte[] bytes) {
        if (bytes.length > properties.getRequestLimit())
            throw new BusinessException(413, "REQUEST_TOO_LARGE", "请求正文超过限制");
        Exchange exchange = new Exchange();
        exchange.setMethod(method);
        exchange.setPath(path);
        exchange.setQueryString(query);
        exchange.setQuery(parseForm(query));
        exchange.setHeaders(normalizeHeaders(headers));
        exchange.setRawBody(decodeUtf8(bytes));
        exchange.setBody(parseBody(exchange));
        return exchange;
    }

    /**
     * 按表单编码规则解析查询或表单正文，保留同名参数的全部值
     *
     * @param encoded 以 & 分隔的原始编码字符串，可为空
     * @return 单值为字符串、多值为列表的有序参数映射
     */
    public Map<String, Object> parseForm(String encoded) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (encoded == null || encoded.length() == 0) return new LinkedHashMap<>();
        try {
            for (String field : encoded.split("&", -1)) {
                String[] pair = field.split("=", 2);
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value =
                        URLDecoder.decode(pair.length == 1 ? "" : pair[1], StandardCharsets.UTF_8);
                values.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "INVALID_REQUEST", "参数编码不合法");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, list) -> result.put(key, collapse(list)));
        return result;
    }

    /**
     * 仅解析 JSON 和表单正文，文本与 XML 保留在 rawBody 中供脚本读取
     *
     * @param exchange 已完成 UTF-8 解码并规范请求头的上下文
     * @return 结构化正文，文本、XML 或空正文返回 null
     */
    private Object parseBody(Exchange exchange) {
        String content =
                String.valueOf(exchange.getHeaders().getOrDefault("content-type", "text/plain"));
        MediaType type;
        try {
            type = MediaType.parseMediaType(content);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(415, "UNSUPPORTED_MEDIA_TYPE", "Content-Type 不支持");
        }
        if (type.getCharset() != null && !StandardCharsets.UTF_8.equals(type.getCharset()))
            throw new BusinessException(415, "UNSUPPORTED_MEDIA_TYPE", "仅支持 UTF-8 请求正文");
        boolean jsonType =
                "application".equals(type.getType())
                        && (type.getSubtype().equals("json")
                                || type.getSubtype().endsWith("+json"));
        boolean form =
                "application/x-www-form-urlencoded"
                        .equals(type.getType() + "/" + type.getSubtype());
        boolean text =
                "text".equals(type.getType())
                        || type.getSubtype().equals("xml")
                        || type.getSubtype().endsWith("+xml");
        if (!jsonType && !form && !text)
            throw new BusinessException(415, "UNSUPPORTED_MEDIA_TYPE", "首版仅支持 JSON、表单和文本请求");
        // 不把空正文当作非法 JSON，文本和 XML 仅通过 rawBody 交给脚本
        if (exchange.getRawBody().length() == 0) return null;
        if (jsonType) return json.read(exchange.getRawBody(), Object.class);
        return form ? parseForm(exchange.getRawBody()) : null;
    }

    /**
     * 统一请求头键名大小写，使脚本和场景条件使用同一查找约定
     *
     * @param input 原始请求头集合
     * @return 名称为小写的请求头集合
     */
    private Map<String, Object> normalizeHeaders(Map<String, Object> input) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        input.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    /**
     * 将单值参数简化为字符串，同时保留同名多值的列表语义
     *
     * @param values 至少包含一个元素的参数值列表
     * @return 单个字符串或完整值列表
     */
    private Object collapse(List<String> values) {
        return values.size() == 1 ? values.getFirst() : values;
    }

    /**
     * 遇到非法字节直接拒绝请求，避免替换字符导致原样报文被静默改写
     *
     * @param bytes 未经解码的请求正文
     * @return 严格解码后的原始正文
     */
    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new BusinessException(415, "UNSUPPORTED_MEDIA_TYPE", "请求正文不是合法 UTF-8");
        }
    }
}
