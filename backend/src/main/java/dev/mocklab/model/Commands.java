package dev.mocklab.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端具有独立校验语义的命令，承载修订校验、发布回滚与接口调试输入
 */
public final class Commands {
    private Commands() {}

    /**
     * 保存草稿或发布当前编辑配置，必须携带页面加载时的修订号
     *
     * @param revision 预期接口修订号，不允许为负数
     * @param config 本次提交的完整接口配置
     */
    public record Save(
            @NotNull @PositiveOrZero Long revision, @NotNull @Valid InterfaceConfig config) {}

    /**
     * 基于当前发布状态生成一个回滚版本，保留现有草稿
     *
     * @param revision 预期接口修订号
     * @param version 要恢复的历史发布版本号
     * @param expectedVersion 页面确认时的当前发布版本，用于防止并发覆盖
     */
    public record Rollback(
            @NotNull Long revision, @NotNull Integer version, @NotNull Integer expectedVersion) {}

    /**
     * 切换接口归档状态，恢复不会直接重新启用历史发布配置
     *
     * @param revision 预期接口修订号
     * @param archived true 表示归档，false 表示恢复
     */
    public record Lifecycle(@NotNull Long revision, boolean archived) {}

    /**
     * 提供单次调试请求，发布调试使用服务端快照而不信任客户端 config
     *
     * @param source draft 使用提交的配置，published 使用服务端发布快照
     * @param revision 草稿来源的修订号，可为空，仅用于诊断展示
     * @param config 待调试的完整草稿配置，发布调试忽略此值
     * @param method 本次模拟的 HTTP 方法
     * @param path 系统命名空间内的请求路径，不包含 /m/{systemKey} 前缀
     * @param query 原始查询字符串，不包含问号，可为空
     * @param headers 请求头集合，同名多值可使用列表，名称在解析时统一为小写
     * @param rawBody 原始 UTF-8 文本正文，可为空
     */
    public record Debug(
            @NotBlank @Pattern(regexp = "draft|published") String source,
            Long revision,
            InterfaceConfig config,
            @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String method,
            @NotBlank String path,
            String query,
            Map<String, Object> headers,
            String rawBody) {
        /**
         * 将省略的请求头规范为空集合，供真实入口与调试入口共用解析流程
         *
         * @param source draft 或 published 调试来源
         * @param revision 草稿修订号，可为空
         * @param config 待调试草稿，发布调试忽略
         * @param method 模拟 HTTP 方法
         * @param path 系统命名空间内的请求路径
         * @param query 原始查询字符串，可为空
         * @param headers 请求头集合，为 null 时转换为空集合
         * @param rawBody 原始 UTF-8 文本正文，可为空
         */
        public Debug {
            headers = headers == null ? new LinkedHashMap<>() : headers;
        }
    }
}
