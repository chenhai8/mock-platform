package dev.mocklab.common;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * 使用固定毫秒精度的 UTC 字符串存储时间，保证数据库字符串排序与时间先后一致
 */
public final class DbTime {
    private static final DateTimeFormatter FORMAT =
            new DateTimeFormatterBuilder().appendInstant(3).toFormatter();

    private DbTime() {}

    /**
     * 生成供字符串列排序使用的当前 UTC 时间，统一保留三位毫秒
     *
     * @return 固定毫秒精度的 UTC 时间文本
     */
    public static String now() {
        return format(Instant.now());
    }

    /**
     * 将时间点格式化为与数据库过滤和排序一致的表示
     *
     * @param instant 待格式化的绝对时间点
     * @return 固定毫秒精度的 UTC 时间文本
     */
    public static String format(Instant instant) {
        return FORMAT.format(instant);
    }
}
