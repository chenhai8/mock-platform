package dev.mocklab.engine;

import dev.mocklab.common.BusinessException;

import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 按路径段匹配动态接口，保留大小写、尾斜杠及静态段的优先级语义
 */
@Component
public class RouteMatcher {
    private static final Pattern PARAM = Pattern.compile("\\{[a-zA-Z][a-zA-Z0-9_]*}");

    /**
     * 抹去参数名称进行路由占用比较，静态路径内容保持原样
     *
     * @param path 已校验的配置路径
     * @return 参数段替换为 {} 的路由形状
     */
    public String shape(String path) {
        return PARAM.matcher(path).replaceAll("{}");
    }

    /**
     * 按相同段数和静态段内容提取参数，参数段必须非空且只解码一次
     *
     * @param template 配置的接口路径模板
     * @param path 实际请求路径
     * @return 匹配成功的参数集合，不匹配时为 null
     */
    public Map<String, Object> parameters(String template, String path) {
        String[] expected = template.split("/", -1), actual = path.split("/", -1);
        if (expected.length != actual.length) return null;
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (int i = 0; i < expected.length; i++) {
            if (PARAM.matcher(expected[i]).matches()) {
                if (actual[i].length() == 0) return null;
                parameters.put(
                        expected[i].substring(1, expected[i].length() - 1),
                        decodeSegment(actual[i]));
            } else if (!expected[i].equals(actual[i])) return null;
        }
        return parameters;
    }

    /**
     * 从左到右比较路径段，首个不同位置的静态段优先于参数段
     *
     * @param a 第一个已匹配模板
     * @param b 第二个已匹配模板
     * @return 负值表示 a 更具体，正值表示 b 更具体，零表示具体程度相同
     */
    public int compareSpecificity(String a, String b) {
        String[] aa = a.split("/", -1), bb = b.split("/", -1);
        for (int i = 0; i < Math.min(aa.length, bb.length); i++) {
            int cmp =
                    Boolean.compare(PARAM.matcher(aa[i]).matches(), PARAM.matcher(bb[i]).matches());
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    /**
     * 在匹配前拒绝无效路径及编码穿越段，避免非法路径被当作普通未命中
     *
     * @param path 系统内实际请求路径
     */
    public void assertRequestPath(String path) {
        if (path == null
                || path.length() > 1000
                || !path.startsWith("/")
                || path.contains("//")
                || path.contains("\\")
                || path.contains("?")
                || path.contains("#"))
            throw new BusinessException(400, "INVALID_REQUEST", "请求路径不合法");
        for (String segment : path.split("/", -1)) decodeSegment(segment);
    }

    /**
     * 只解码单个路径段并保留加号字面值，禁止解码后引入分隔符或穿越段
     *
     * @param segment 尚未解码的单个路径段
     * @return 经过校验的解码路径段
     */
    private String decodeSegment(String segment) {
        try {
            // 路径中的加号不是表单空格，先保护字面加号再进行百分号解码
            String decoded = URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
            if (decoded.contains("/")
                    || decoded.contains("\\")
                    || decoded.equals(".")
                    || decoded.equals("..")
                    || decoded.chars().anyMatch(c -> c < 32 || c == 127))
                throw new IllegalArgumentException("Invalid path segment");
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "INVALID_REQUEST", "请求路径包含非法编码或路径段");
        }
    }
}
