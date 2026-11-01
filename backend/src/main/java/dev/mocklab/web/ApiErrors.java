package dev.mocklab.web;

import dev.mocklab.common.BusinessException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 将管理接口异常转换为统一错误结构，对外隐藏内部堆栈和基础设施信息
 */
@Slf4j
@RestControllerAdvice(basePackages = "dev.mocklab.web")
public class ApiErrors {
    /**
     * 保留业务异常中的状态、错误码和可公开说明，统一管理接口响应结构
     *
     * @param error 领域或编排层业务异常
     * @return 管理接口错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> business(BusinessException error) {
        return ResponseEntity.status(error.getStatus())
                .body(
                        Map.of(
                                "error",
                                Map.of("code", error.getCode(), "message", error.getMessage())));
    }

    /**
     * 将参数绑定和输入校验失败统一为可公开的请求错误提示
     *
     * @param error 参数校验、JSON 解析或非法输入异常
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<Object> validation(Exception error) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "error",
                                Map.of(
                                        "code",
                                        "INVALID_REQUEST",
                                        "message",
                                        "输入不合法，请检查必填项、类型和字段长度")));
    }

    /**
     * 将唯一约束冲突转为配置占用提示，避免暴露数据库错误细节
     *
     * @param error 数据库唯一键冲突
     * @return HTTP 409 错误响应
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Object> duplicate(DuplicateKeyException error) {
        return ResponseEntity.status(409)
                .body(Map.of("error", Map.of("code", "CONFLICT", "message", "系统标识或配置已被占用")));
    }

    /**
     * 记录完整内部异常，对管理端仅返回可操作的服务不可用提示
     *
     * @param error 未被已知处理器转换的异常
     * @return HTTP 503 错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> failure(Exception error) {
        log.error("Management request failed", error);
        return ResponseEntity.status(503)
                .body(
                        Map.of(
                                "error",
                                Map.of(
                                        "code",
                                        "SERVICE_UNAVAILABLE",
                                        "message",
                                        "服务暂不可用，请检查服务日志")));
    }
}
