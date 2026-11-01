package dev.mocklab.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import dev.mocklab.common.DbTime;
import dev.mocklab.config.MockProperties;
import dev.mocklab.entity.MockCallLog;
import dev.mocklab.mapper.MockCallLogMapper;
import dev.mocklab.service.CallLogService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 集中处理调用日志筛选和分批清理，限制单次数据库访问规模
 */
@Service
@RequiredArgsConstructor
public class CallLogServiceImpl extends ServiceImpl<MockCallLogMapper, MockCallLog>
        implements CallLogService {
    private static final int CLEANUP_BATCH = 1000;
    private final MockProperties properties;

    @Override
    public Page<MockCallLog> search(
            Page<MockCallLog> page, MockCallLog filter, String from, String to) {
        page.setCurrent(Math.max(1, page.getCurrent()));
        page.setSize(Math.min(100, Math.max(1, page.getSize())));
        LambdaQueryChainWrapper<MockCallLog> query =
                lambdaQuery()
                        .eq(
                                filter.getSystemId() != null,
                                MockCallLog::getSystemId,
                                filter.getSystemId())
                        .eq(
                                filter.getEndpointId() != null,
                                MockCallLog::getEndpointId,
                                filter.getEndpointId());
        applyResultFilters(query, filter);
        query.ge(StringUtils.hasText(from), MockCallLog::getCreatedAt, from)
                .le(StringUtils.hasText(to), MockCallLog::getCreatedAt, to);
        return query.orderByDesc(MockCallLog::getId).page(page);
    }

    /**
     * 应用调用结果筛选，所有条件保持在日志领域
     *
     * @param query 当前日志查询
     * @param filter 结果筛选参数
     */
    private void applyResultFilters(
            LambdaQueryChainWrapper<MockCallLog> query, MockCallLog filter) {
        query.eq(filter.getStatus() != null, MockCallLog::getStatus, filter.getStatus())
                .eq(
                        StringUtils.hasText(filter.getSource()),
                        MockCallLog::getSource,
                        filter.getSource())
                .eq(
                        StringUtils.hasText(filter.getErrorCode()),
                        MockCallLog::getErrorCode,
                        filter.getErrorCode())
                .like(
                        StringUtils.hasText(filter.getScene()),
                        MockCallLog::getScene,
                        filter.getScene())
                .like(
                        StringUtils.hasText(filter.getRequestPath()),
                        MockCallLog::getRequestPath,
                        filter.getRequestPath());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void prune() {
        removeBatch(expiredIds());
        long excess = count() - properties.getLogMaxRows();
        if (excess <= 0) return;
        removeBatch(excessIds(excess));
    }

    /**
     * 固定批次选择过期主键，避免一次删除大量日志
     *
     * @return 本批过期日志主键
     */
    private List<Long> expiredIds() {
        String cutoff =
                DbTime.format(Instant.now().minus(properties.getLogDays(), ChronoUnit.DAYS));
        return lambdaQuery()
                .select(MockCallLog::getId)
                .lt(MockCallLog::getCreatedAt, cutoff)
                .orderByAsc(MockCallLog::getId)
                .last("LIMIT " + CLEANUP_BATCH)
                .list()
                .stream()
                .map(MockCallLog::getId)
                .toList();
    }

    /**
     * 选择超过数量上限的最早一批日志
     *
     * @param excess 超出总量
     * @return 本批待清理主键
     */
    private List<Long> excessIds(long excess) {
        return lambdaQuery()
                .select(MockCallLog::getId)
                .orderByAsc(MockCallLog::getId)
                .last("LIMIT " + Math.min(excess, CLEANUP_BATCH))
                .list()
                .stream()
                .map(MockCallLog::getId)
                .toList();
    }

    /**
     * 事务由调用方公开方法 prune 生效，按主键批量删除避免逐条写入
     *
     * @param ids 待清理主键
     */
    @SuppressWarnings("SpringTransactionalMethodCallsInspection")
    private void removeBatch(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) return;
        removeByIds(ids);
    }
}
