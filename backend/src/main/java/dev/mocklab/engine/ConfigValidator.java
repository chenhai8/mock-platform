package dev.mocklab.engine;

import dev.mocklab.common.BusinessException;
import dev.mocklab.config.MockProperties;
import dev.mocklab.model.InterfaceConfig;
import dev.mocklab.model.Scene;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 在保存和发布边界校验接口与场景，发布时额外通过 runner 检查脚本语法
 */
@Component
@RequiredArgsConstructor
public class ConfigValidator {
    private static final Set<String> METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> OPERATORS =
            Set.of("eq", "ne", "contains", "exists", "missing");
    private static final Pattern PARAM = Pattern.compile("\\{[a-zA-Z][a-zA-Z0-9_]*}");
    private final MockProperties properties;
    private final HeaderPolicy headers;
    private final RunnerClient runner;

    /**
     * 校验完整接口配置，默认场景及优先级约束在所有场景间统一检查
     *
     * @param config 待保存或发布的完整配置
     * @param publishing 是否处于发布阶段，发布时才调用 runner 进行语法检查
     */
    public void validate(InterfaceConfig config, boolean publishing) {
        if (config == null) invalid("接口配置不能为空");
        assertLength(config.getName(), 1, 80, "接口名称");
        assertLength(config.getDescription(), 0, 1000, "接口说明");
        assertPath(config.getPath());
        if (config.getMethod() == null || !METHODS.contains(config.getMethod()))
            invalid("请求方法不支持");
        if (CollectionUtils.isEmpty(config.getRules()) || config.getRules().size() > 50)
            invalid("响应场景数量必须为 1～50");
        if (config.getRules().stream().anyMatch(Objects::isNull)) invalid("场景不能为空");
        // 默认场景唯一且不能停用，保证任意合法请求都有确定的兜底响应
        if (config.getRules().stream().filter(Scene::isFallback).count() != 1)
            invalid("必须包含且只包含一个默认场景");
        Set<Integer> priorities = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (Scene scene : config.getRules()) assertScene(scene, priorities, ids, publishing);
    }

    /**
     * 限制配置路径为可预测的单段参数模板，禁止编码和路径穿越形式
     *
     * @param path 不含系统前缀、查询字符串和片段的接口路径
     */
    public void assertPath(String path) {
        assertLength(path, 1, 500, "接口路径");
        if (!path.startsWith("/")
                || path.contains("//")
                || path.chars().anyMatch(c -> c < 33 || c > 126)
                || path.contains("?")
                || path.contains("#")
                || path.contains("\\")
                || path.contains("%")) invalid("接口路径不合法");
        Set<String> names = new HashSet<>();
        for (String segment : path.split("/", -1)) {
            if (PARAM.matcher(segment).matches()) {
                if (!names.add(segment)) invalid("路径参数名不能重复");
            } else if (!segment.matches("[a-zA-Z0-9._~!$&'()+,;=:@-]*")
                    || segment.equals(".")
                    || segment.equals("..")) invalid("路径段不合法");
        }
    }

    /**
     * 检查场景标识、默认兜底规则及响应约束，并维护接口内的唯一性集合
     *
     * @param scene 待检查场景
     * @param priorities 已使用的非默认场景优先级
     * @param ids 已使用的场景标识
     * @param publishing 发布时需要验证脚本语法
     */
    private void assertScene(
            Scene scene, Set<Integer> priorities, Set<String> ids, boolean publishing) {
        if (scene == null) invalid("场景不能为空");
        assertLength(scene.getId(), 1, 80, "场景标识");
        if (!ids.add(scene.getId())) invalid("场景标识不能重复");
        assertLength(scene.getName(), 1, 50, "场景名称");
        if (scene.isFallback() && !scene.isEnabled()) invalid("默认场景不能停用");
        if (!scene.isFallback()) assertCondition(scene, priorities);
        if (scene.getStatus() < 200 || scene.getStatus() > 599) invalid("状态码须为 200～599");
        if (scene.getDelay() < 0 || scene.getDelay() > properties.getDelayLimitMs())
            invalid("响应延迟超出允许范围");
        headers.merge(scene.getContentType(), scene.getHeaders(), Map.of());
        assertSource(scene, publishing);
    }

    /**
     * 仅允许标量字段比较及存在性条件，并禁止非默认场景优先级冲突
     *
     * @param scene 非默认场景
     * @param priorities 当前接口已占用的优先级集合
     */
    private void assertCondition(Scene scene, Set<Integer> priorities) {
        if (scene.getPriority() < 1
                || scene.getPriority() > 9999
                || !priorities.add(scene.getPriority())) invalid("优先级须为 1～9999 且不能重复");
        if (scene.getField() == null
                || !scene.getField().matches("(body|query|headers|pathParams)\\.[a-zA-Z0-9_.-]+")) invalid("条件字段不合法");
        if (scene.getOperator() == null || !OPERATORS.contains(scene.getOperator()))
            invalid("条件运算符不支持");
        if (scene.getValue() == null) invalid("匹配值不能为空，可使用空字符串");
    }

    /**
     * 按响应模式限制 UTF-8 大小并校验无正文状态码，脚本发布前只做语法检查
     *
     * @param scene 包含正文或脚本源码的场景
     * @param publishing 是否执行发布前语法校验
     */
    private void assertSource(Scene scene, boolean publishing) {
        if (scene.getCode() == null) invalid("正文或脚本不能为空，可使用空字符串");
        int bytes = scene.getCode().getBytes(StandardCharsets.UTF_8).length;
        if ("raw".equals(scene.getMode())) {
            if (bytes > properties.getResponseLimit()) invalid("响应正文超过大小限制");
            if ((scene.getStatus() == 204 || scene.getStatus() == 304) && bytes > 0)
                invalid("204/304 响应不能包含正文");
        } else if ("script".equals(scene.getMode())) {
            if (bytes > properties.getScriptLimit()) invalid("脚本超过大小限制");
            // 保存草稿允许暂存未完成脚本，只有发布才要求语法通过
            if (publishing) assertSyntax(scene.getCode());
        } else invalid("响应模式不支持");
    }

    /**
     * 将脚本语法错误转换为配置错误，runner 不可用或执行超时保持原错误状态
     *
     * @param code 待发布的脚本源码
     */
    private void assertSyntax(String code) {
        try {
            runner.run("validate", code, Map.of());
        } catch (BusinessException error) {
            if (error.getStatus() == 503 || error.getStatus() == 504) throw error;
            throw new BusinessException(400, "INVALID_SCRIPT", error.getMessage());
        }
    }

    /**
     * 同时限制原始文本长度和去除首尾空白后的最小长度，避免空白必填值
     *
     * @param value 待检查文本
     * @param min 去除首尾空白后的最小长度
     * @param max 原始文本最大长度
     * @param name 错误提示使用的字段名称
     */
    private void assertLength(String value, int min, int max, String name) {
        if (value == null || value.length() > max || value.trim().length() < min)
            invalid(name + "长度必须为 " + min + "～" + max);
    }

    /**
     * 统一抛出配置校验失败，保持管理端可识别的错误码
     *
     * @param message 具体配置约束说明
     */
    private void invalid(String message) {
        throw new BusinessException(400, "INVALID_CONFIG", message);
    }
}
