package dev.mocklab.service;

import com.baomidou.mybatisplus.extension.service.IService;

import dev.mocklab.entity.MockOperation;

import java.util.List;

/**
 * 提供关键配置操作记录的领域查询
 */
public interface OperationService extends IService<MockOperation> {
    /**
     * 查询近期关键操作
     *
     * @return 最近一百条操作
     */
    List<MockOperation> recent();
}
