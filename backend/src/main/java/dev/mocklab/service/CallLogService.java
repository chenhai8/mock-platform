package dev.mocklab.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import dev.mocklab.entity.MockCallLog;

/**
 * 提供调用日志领域的分页查询及保留策略清理
 */
public interface CallLogService extends IService<MockCallLog> {
    /**
     * 分页查询调用摘要
     *
     * @param page 分页参数
     * @param filter 系统、接口、来源、状态及场景条件
     * @param from 起始 UTC 时间，可为空
     * @param to 结束 UTC 时间，可为空
     * @return 调用记录页
     */
    Page<MockCallLog> search(Page<MockCallLog> page, MockCallLog filter, String from, String to);

    /** 按保留期和最大数量固定分批清理 */
    void prune();
}
