package dev.mocklab.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import dev.mocklab.common.BusinessException;
import dev.mocklab.common.DbTime;
import dev.mocklab.common.JsonCodec;
import dev.mocklab.entity.MockEndpoint;
import dev.mocklab.mapper.MockEndpointMapper;
import dev.mocklab.model.InterfaceConfig;
import dev.mocklab.service.EndpointService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在接口表领域内解析配置快照，并以修订号执行条件更新
 */
@Service
@RequiredArgsConstructor
public class EndpointServiceImpl extends ServiceImpl<MockEndpointMapper, MockEndpoint>
        implements EndpointService {
    private final JsonCodec json;

    @Override
    public List<MockEndpoint> findBySystem(Long systemId) {
        return lambdaQuery()
                .eq(MockEndpoint::getSystemId, systemId)
                .orderByAsc(MockEndpoint::getId)
                .list()
                .stream()
                .map(this::hydrate)
                .toList();
    }

    @Override
    public MockEndpoint loadOrThrow(Long id) {
        MockEndpoint endpoint = getById(id);
        if (endpoint == null) throw new BusinessException(404, "NOT_FOUND", "接口不存在");
        return hydrate(endpoint);
    }

    @Override
    public MockEndpoint saveRevision(MockEndpoint endpoint, long expectedRevision) {
        endpoint.setDraftJson(json.write(endpoint.getDraft()));
        endpoint.setUpdatedAt(DbTime.now());
        endpoint.setRevision(expectedRevision + 1);
        // 将预期修订号放入更新条件，只有数据库实际命中当前版本才视为保存成功
        boolean saved =
                lambdaUpdate()
                        .eq(MockEndpoint::getId, endpoint.getId())
                        .eq(MockEndpoint::getRevision, expectedRevision)
                        .update(endpoint);
        if (!saved) throw new BusinessException(409, "REVISION_CONFLICT", "配置已变化，请重新加载");
        return endpoint;
    }

    /**
     * 将配置快照转换为可展示结构，不查询其他表
     *
     * @param endpoint 当前接口实体
     * @return 带解析配置的接口实体
     */
    private MockEndpoint hydrate(MockEndpoint endpoint) {
        endpoint.setDraft(json.read(endpoint.getDraftJson(), InterfaceConfig.class));
        if (endpoint.getPublishedJson() != null)
            endpoint.setPublished(json.read(endpoint.getPublishedJson(), InterfaceConfig.class));
        return endpoint;
    }
}
