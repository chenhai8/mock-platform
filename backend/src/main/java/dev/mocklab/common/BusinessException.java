package dev.mocklab.common;

import lombok.Getter;

import java.util.Map;

/**
 * 携带可公开错误码、HTTP 状态及必要响应头的统一业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    /**
     * 对外返回的 HTTP 状态码
     */
    private final int status;
    /**
     * 前端与调用方可识别的稳定错误码
     */
    private final String code;
    /**
     * 错误响应附带的协议头，例如方法不匹配时的 Allow
     */
    private final Map<String, String> headers;

    /**
     * 创建不携带额外协议头的业务异常
     *
     * @param status 对外 HTTP 状态码
     * @param code 稳定错误码
     * @param message 可公开的错误说明
     */
    public BusinessException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    /**
     * 创建需要附带协议头的业务异常，例如方法不匹配时提供 Allow
     *
     * @param status 对外 HTTP 状态码
     * @param code 稳定错误码
     * @param message 可公开的错误说明
     * @param headers 错误响应附加头
     */
    public BusinessException(int status, String code, String message, Map<String, String> headers) {
        super(message);
        this.status = status;
        this.code = code;
        this.headers = headers;
    }
}
