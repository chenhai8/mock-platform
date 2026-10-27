package dev.mocklab.service;

import com.baomidou.mybatisplus.extension.service.IService;

import dev.mocklab.entity.MockVersion;
import dev.mocklab.model.InterfaceConfig;

import java.util.List;

/**
 * 查询不可变发布历史并加载回滚所需的配置快照
 */
public interface VersionService extends IService<MockVersion> {
    /**
     * 获取接口发布历史
     *
     * @param endpointId 接口主键
     * @return 降序发布历史
     */
    List<MockVersion> history(Long endpointId);

    /**
     * 加载指定历史快照
     *
     * @param endpointId 接口主键
     * @param version 版本号
     * @return 配置快照
     */
    InterfaceConfig loadConfigOrThrow(Long endpointId, int version);
}
