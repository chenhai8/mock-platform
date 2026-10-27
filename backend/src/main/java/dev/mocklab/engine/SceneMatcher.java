package dev.mocklab.engine;

import dev.mocklab.common.JsonCodec;
import dev.mocklab.model.Exchange;
import dev.mocklab.model.Scene;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 按场景优先级匹配单字段条件，最后使用始终启用的默认场景兜底
 */
@Component
@RequiredArgsConstructor
public class SceneMatcher {
    private static final Object MISSING = new Object();
    private final JsonCodec json;

    /**
     * 先匹配已启用的非默认场景，按优先级降序选择，默认场景最后兜底
     *
     * @param scenes 已通过完整配置校验的场景列表
     * @param request 当前调用上下文
     * @return 第一个命中的场景
     */
    public Scene select(List<Scene> scenes, Exchange request) {
        Map context = json.read(json.write(request), Map.class);
        return scenes.stream()
                .filter(Scene::isEnabled)
                .sorted(
                        Comparator.comparing(Scene::isFallback)
                                .thenComparing(
                                        Comparator.comparingInt(Scene::getPriority).reversed()))
                .filter(s -> matches(s, context))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 区分字段缺失与显式 null，普通比较仅针对非空标量的字符串表示
     *
     * @param scene 待判断场景
     * @param context 转换为映射的请求上下文
     * @return 是否满足场景条件，默认场景始终满足
     */
    private boolean matches(Scene scene, Map<?, ?> context) {
        if (scene.isFallback()) return true;
        Object value = lookup(context, scene.getField());
        // 存在性只判断字段是否出现，显式 null 与字段缺失具有不同语义
        if ("exists".equals(scene.getOperator())) return value != MISSING;
        if ("missing".equals(scene.getOperator())) return value == MISSING;
        if (value == MISSING || value == null || value instanceof Map || value instanceof List)
            return false;
        String actual = String.valueOf(value);
        return switch (scene.getOperator()) {
            case "eq" -> actual.equals(scene.getValue());
            case "ne" -> !actual.equals(scene.getValue());
            case "contains" -> actual.contains(scene.getValue());
            default -> false;
        };
    }

    /**
     * 沿点分路径读取对象字段或数组索引，未找到时保留独立的缺失标记
     *
     * @param current 当前遍历节点
     * @param path 条件配置的点分字段路径
     * @return 字段值或 MISSING 标记，显式 null 不等于字段缺失
     */
    private Object lookup(Object current, String path) {
        for (String part : path.split("\\.")) {
            if (current instanceof Map<?, ?> map)
                current = map.containsKey(part) ? map.get(part) : MISSING;
            else if (current instanceof List<?> list && part.matches("\\d+")) {
                try {
                    int index = Integer.parseInt(part);
                    current = index < list.size() ? list.get(index) : MISSING;
                } catch (NumberFormatException ex) {
                    return MISSING;
                }
            } else return MISSING;
        }
        return current;
    }
}
