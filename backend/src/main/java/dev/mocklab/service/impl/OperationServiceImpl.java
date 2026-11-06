package dev.mocklab.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import dev.mocklab.common.DbTime;
import dev.mocklab.entity.MockOperation;
import dev.mocklab.logging.constants.OperationLogConstants;
import dev.mocklab.mapper.MockOperationMapper;
import dev.mocklab.service.OperationService;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * 按操作时间和主键分页查询关键配置操作，限制默认检索范围
 */
@Service
public class OperationServiceImpl extends ServiceImpl<MockOperationMapper, MockOperation>
        implements OperationService {
    @Override
    public Page<MockOperation> search(Page<MockOperation> page, String from, String to) {
        page.setCurrent(Math.max(1, page.getCurrent()));
        page.setSize(Math.min(OperationLogConstants.MAX_PAGE_SIZE, Math.max(1, page.getSize())));
        LambdaQueryChainWrapper<MockOperation> query = lambdaQuery();
        applyTimeFilters(query, from, to);
        return query.orderByDesc(MockOperation::getCreatedAt)
                .orderByDesc(MockOperation::getId)
                .page(page);
    }

    /**
     * 未指定时间时限定最近一个月，显式时间筛选保留单边或双边语义
     *
     * @param query 当前操作记录查询
     * @param from 起始 UTC 时间，可为空
     * @param to 结束 UTC 时间，可为空
     */
    private void applyTimeFilters(
            LambdaQueryChainWrapper<MockOperation> query, String from, String to) {
        if (StringUtils.hasText(from) || StringUtils.hasText(to)) {
            query.ge(StringUtils.hasText(from), MockOperation::getCreatedAt, from)
                    .le(StringUtils.hasText(to), MockOperation::getCreatedAt, to);
            return;
        }
        // 同一 UTC 时间点生成范围，过滤与排序共同匹配操作时间索引
        ZonedDateTime end = ZonedDateTime.now(ZoneOffset.UTC);
        String start =
                DbTime.format(end.minusMonths(OperationLogConstants.DEFAULT_QUERY_MONTHS).toInstant());
        query.ge(MockOperation::getCreatedAt, start)
                .le(MockOperation::getCreatedAt, DbTime.format(end.toInstant()));
    }
}
