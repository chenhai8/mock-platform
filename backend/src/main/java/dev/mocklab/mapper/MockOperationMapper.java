package dev.mocklab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import dev.mocklab.entity.MockOperation;

/**
 * 提供 MockOperation 对应表的 MyBatis-Plus 基础数据访问，业务条件由表 Service 管理
 */
public interface MockOperationMapper extends BaseMapper<MockOperation> {}
