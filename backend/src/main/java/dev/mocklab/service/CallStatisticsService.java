package dev.mocklab.service;

import com.baomidou.mybatisplus.extension.service.IService;

import dev.mocklab.common.BusinessException;
import dev.mocklab.entity.MockCallStatistics;
import dev.mocklab.model.CallStatisticsSummary;

import java.time.Instant;

/**
 * 管理独立于日志保留策略的系统年度真实调用计数
 */
public interface CallStatisticsService extends IService<MockCallStatistics> {
    /**
     * 真实请求在到达时所属年份计数一次，不接入可丢弃的日志队列
     *
     * @param systemId 匹配到的系统主键，未匹配时为空
     * @param receivedAt 请求到达时间，按 UTC 划分自然年
     */
    void increment(Long systemId, Instant receivedAt);

    /**
     * 查询顶部跨系统与跨年度调用概览，不受系统归档和日志清理影响
     *
     * @param systemId 当前系统主键，未选择时为空
     * @return 同一查询快照中的累计、本年及当前系统本年次数
     * @throws BusinessException 当前系统主键不是正数
     */
    CallStatisticsSummary summarize(Long systemId);
}
