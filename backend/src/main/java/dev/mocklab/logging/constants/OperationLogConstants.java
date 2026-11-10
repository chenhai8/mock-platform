package dev.mocklab.logging.constants;

/**
 * 关键配置操作记录的操作标识、查询范围与分页限制
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

    /**
     * 不创建发布版本的场景启停操作
     */
    public static final String SCENE_STATE_ACTION = "SCENE_STATE";

    /**
     * 不创建发布版本的场景延迟调整
     */
    public static final String SCENE_DELAY_ACTION = "SCENE_DELAY";

    private OperationLogConstants() {}
}
