package dev.mocklab.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import dev.mocklab.common.BusinessException;
import dev.mocklab.common.JsonCodec;
import dev.mocklab.entity.MockVersion;
import dev.mocklab.mapper.MockVersionMapper;
import dev.mocklab.model.InterfaceConfig;
import dev.mocklab.service.VersionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在版本表领域内读取历史配置，避免跨表拼装查询条件
 */
@Service
@RequiredArgsConstructor
public class VersionServiceImpl extends ServiceImpl<MockVersionMapper, MockVersion>
        implements VersionService {
    private final JsonCodec json;

    @Override
    public List<MockVersion> history(Long endpointId) {
        return lambdaQuery()
                .eq(MockVersion::getEndpointId, endpointId)
                .orderByDesc(MockVersion::getVersion)
                .list()
                .stream()
                .map(this::hydrate)
                .toList();
    }

    @Override
    public InterfaceConfig loadConfigOrThrow(Long endpointId, int version) {
        MockVersion snapshot =
                lambdaQuery()
                        .eq(MockVersion::getEndpointId, endpointId)
                        .eq(MockVersion::getVersion, version)
                        .one();
        if (snapshot == null) throw new BusinessException(404, "NOT_FOUND", "发布版本不存在");
        return json.read(snapshot.getConfigJson(), InterfaceConfig.class);
    }

    /**
     * 解析不可变历史配置用于查看
     *
     * @param snapshot 历史实体
     * @return 带配置的历史实体
     */
    private MockVersion hydrate(MockVersion snapshot) {
        snapshot.setConfig(json.read(snapshot.getConfigJson(), InterfaceConfig.class));
        return snapshot;
    }
}
