package dev.mocklab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import dev.mocklab.entity.MockCallStatistics;
import dev.mocklab.logging.constants.CallStatisticsConstants;
import dev.mocklab.model.CallStatisticsSummary;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 使用 MySQL 5.7 支持的原子插入更新与汇总 SQL 访问系统年度计数
 */
public interface MockCallStatisticsMapper extends BaseMapper<MockCallStatistics> {
    /**
     * 唯一键冲突时在数据库内加一，禁止先读取次数再覆盖写回
     *
     * @param systemId 系统主键或未匹配系统的保留标识
     * @param statYear 请求到达时所属的 UTC 自然年
     */
    @Insert("""
            INSERT INTO mock_call_statistics (system_id, stat_year, call_count)
            VALUES (#{systemId}, #{statYear}, 1)
            ON DUPLICATE KEY UPDATE call_count = call_count + 1
            """)
    @Options(timeout = CallStatisticsConstants.SQL_TIMEOUT_SECONDS)
    void increment(@Param("systemId") long systemId, @Param("statYear") int statYear);

    /**
     * 单次扫描小规模年度汇总，不访问明细日志或按系统逐个查询
     *
     * @param systemId 当前系统主键，可为空
     * @param statYear 本次查询的 UTC 自然年
     * @return 包含全平台累计、本年及当前系统本年次数的概览
     */
    @Select("""
            SELECT #{statYear} AS stat_year,
                   COALESCE(SUM(call_count), 0) AS total_calls,
                   COALESCE(SUM(CASE WHEN stat_year = #{statYear}
                       THEN call_count ELSE 0 END), 0) AS year_calls,
                   COALESCE(SUM(CASE WHEN stat_year = #{statYear} AND system_id = #{systemId}
                       THEN call_count ELSE 0 END), 0) AS system_year_calls
            FROM mock_call_statistics
            """)
    @Options(timeout = CallStatisticsConstants.SQL_TIMEOUT_SECONDS)
    CallStatisticsSummary summarize(@Param("systemId") Long systemId, @Param("statYear") int statYear);
}
