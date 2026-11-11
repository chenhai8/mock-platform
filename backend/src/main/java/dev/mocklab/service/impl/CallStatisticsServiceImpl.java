package dev.mocklab.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import dev.mocklab.common.BusinessException;
import dev.mocklab.entity.MockCallStatistics;
import dev.mocklab.logging.constants.CallStatisticsConstants;
import dev.mocklab.mapper.MockCallStatisticsMapper;
import dev.mocklab.model.CallStatisticsSummary;
import dev.mocklab.service.CallStatisticsService;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;

/**
 * 以系统和 UTC 自然年为唯一维度原子计数，查询仅扫描年度汇总
 */
@Service
public class CallStatisticsServiceImpl extends ServiceImpl<MockCallStatisticsMapper, MockCallStatistics>
        implements CallStatisticsService {
    @Override
    public void increment(Long systemId, Instant receivedAt) {
        long owner = systemId == null ? CallStatisticsConstants.UNASSIGNED_SYSTEM_ID : systemId;
        baseMapper.increment(owner, receivedAt.atOffset(ZoneOffset.UTC).getYear());
    }

    @Override
    public CallStatisticsSummary summarize(Long systemId) {
        if (systemId != null && systemId <= CallStatisticsConstants.UNASSIGNED_SYSTEM_ID)
            throw new BusinessException(400, "INVALID_REQUEST", "输入不合法，请检查必填项、类型和字段长度");
        return baseMapper.summarize(systemId, Year.now(ZoneOffset.UTC).getValue());
    }
}
