package dev.mocklab.logging.constants;

/**
 * 关键配置操作记录的查询范围与分页限制
 */
public final class OperationLogConstants {
    /**
     * 未指定起止时间时查询的自然月数
     */
    public static final int DEFAULT_QUERY_MONTHS = 1;

    /**
     * 单次操作记录查询允许返回的最大条数
     */
    public static final int MAX_PAGE_SIZE = 100;

    private OperationLogConstants() {}
}
