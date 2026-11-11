package dev.mocklab.logging.constants;

/**
 * 真实调用统计的归属约定与数据库访问边界
 */
public final class CallStatisticsConstants {
    /**
     * 未匹配系统的请求使用独立归属，避免写入可空唯一键
     */
    public static final long UNASSIGNED_SYSTEM_ID = 0L;
    /**
     * 统计 SQL 的最大执行等待秒数，失败不自动重试
     */
    public static final int SQL_TIMEOUT_SECONDS = 3;

    private CallStatisticsConstants() {}
}
