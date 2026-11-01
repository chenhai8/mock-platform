package dev.mocklab.service;

import com.baomidou.mybatisplus.extension.service.IService;

import dev.mocklab.entity.MockEndpoint;

import java.util.List;

/**
 * 管理接口表及其 JSON 快照解析，不在此层操作其他业务表
 */
public interface EndpointService extends IService<MockEndpoint> {
    /**
     * 查询系统的接口配置
     *
     * @param systemId 系统主键
     * @return 接口列表，包含草稿与发布数据
     */
    List<MockEndpoint> findBySystem(Long systemId);

    /**
     * 读取接口并解析配置
     *
     * @param id 接口主键
     * @return 当前接口
     */
    MockEndpoint loadOrThrow(Long id);

    /**
     * 乐观锁保存配置，由调用方事务保证跨表一致性
     *
     * @param endpoint 待保存接口
     * @param expectedRevision 期望修订号
     * @return 更新后接口
     */
    MockEndpoint saveRevision(MockEndpoint endpoint, long expectedRevision);
}
