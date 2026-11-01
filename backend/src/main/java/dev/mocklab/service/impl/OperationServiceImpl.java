package dev.mocklab.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import dev.mocklab.entity.MockOperation;
import dev.mocklab.mapper.MockOperationMapper;
import dev.mocklab.service.OperationService;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按时间顺序对应的自增主键查询最近的配置操作
 */
@Service
public class OperationServiceImpl extends ServiceImpl<MockOperationMapper, MockOperation>
        implements OperationService {
    @Override
    public List<MockOperation> recent() {
        return lambdaQuery().orderByDesc(MockOperation::getId).last("LIMIT 100").list();
    }
}
