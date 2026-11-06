package dev.mocklab.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import dev.mocklab.entity.MockOperation;

/**
 * 提供关键配置操作记录的领域查询
 */
public interface OperationService extends IService<MockOperation> {
    /**
     * 分页查询关键操作，未指定起止时间时默认查询最近一个月
     *
     * @param page 分页参数，单页条数受查询上限约束
     * @param from 起始 UTC 时间，可为空
     * @param to 结束 UTC 时间，可为空
     * @return 操作记录页
     */
    Page<MockOperation> search(Page<MockOperation> page, String from, String to);
}
