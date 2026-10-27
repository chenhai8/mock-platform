package dev.mocklab.service.impl;

import dev.mocklab.common.BusinessException;
import dev.mocklab.common.DbTime;
import dev.mocklab.common.JsonCodec;
import dev.mocklab.engine.ConfigValidator;
import dev.mocklab.engine.RouteMatcher;
import dev.mocklab.entity.MockEndpoint;
import dev.mocklab.entity.MockOperation;
import dev.mocklab.entity.MockSystem;
import dev.mocklab.entity.MockVersion;
import dev.mocklab.model.Commands;
import dev.mocklab.model.InterfaceConfig;
import dev.mocklab.service.ConfigurationService;
import dev.mocklab.service.EndpointService;
import dev.mocklab.service.OperationService;
import dev.mocklab.service.SystemService;
import dev.mocklab.service.VersionService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 在公开事务内协调多个表 Service，保证路由占用、发布快照和操作记录一致
 */
@Service
@RequiredArgsConstructor
public class ConfigurationServiceImpl implements ConfigurationService {
    private final SystemService systems;
    private final EndpointService endpoints;
    private final VersionService versions;
    private final OperationService operations;
    private final ConfigValidator validator;
    private final RouteMatcher routes;
    private final JsonCodec json;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockSystem saveSystem(MockSystem input, String address) {
        MockSystem saved = systems.saveSettings(input);
        recordOperation("SYSTEM", saved.getId(), "SAVE_SETTINGS", "系统设置已更新", address);
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockEndpoint create(MockEndpoint input, String address) {
        if (input.getSystemId() == null || input.getDraft() == null)
            throw new BusinessException(400, "INVALID_CONFIG", "系统和接口草稿不能为空");
        MockSystem system = systems.lockOrThrow(input.getSystemId());
        assertWritableSystem(system);
        validator.validate(input.getDraft(), false);
        assertRouteAvailable(input.getSystemId(), null, input.getDraft());
        MockEndpoint created = assembleEndpoint(input);
        endpoints.save(created);
        recordOperation("ENDPOINT", created.getId(), "CREATE", "创建未发布草稿", address);
        return created;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockEndpoint saveDraft(Long id, Commands.Save command) {
        // 系统行锁与接口修订检查由既有入口完成，跨表写入仍共用公开方法事务
        MockEndpoint current = loadEditableOrThrow(id, command.revision());
        validator.validate(command.config(), false);
        assertRouteAvailable(current.getSystemId(), id, command.config());
        current.setDraft(command.config());
        return endpoints.saveRevision(current, command.revision());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockEndpoint publish(Long id, Commands.Save command, String address) {
        // 系统行锁与接口修订检查由既有入口完成，跨表写入仍共用公开方法事务
        MockEndpoint current = loadEditableOrThrow(id, command.revision());
        assertPublishable(current, command.config());
        current.setDraft(command.config());
        // 历史快照、发布指针与操作记录必须同一事务提交，避免出现半发布状态
        activateSnapshot(current, command.config(), "PUBLISH", null);
        MockEndpoint saved = endpoints.saveRevision(current, command.revision());
        recordOperation("ENDPOINT", id, "PUBLISH", "发布 v" + saved.getPublishedVersion(), address);
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockEndpoint rollback(Long id, Commands.Rollback command, String address) {
        // 系统行锁与接口修订检查由既有入口完成，跨表写入仍共用公开方法事务
        MockEndpoint current = loadEditableOrThrow(id, command.revision());
        if (!Objects.equals(current.getPublishedVersion(), command.expectedVersion()))
            throw new BusinessException(409, "REVISION_CONFLICT", "发布版本已变化，请重新加载");
        InterfaceConfig config = versions.loadConfigOrThrow(id, command.version());
        assertPublishable(current, config);
        // 回滚只切换发布快照并追加新历史，不覆盖 current 中的草稿
        activateSnapshot(current, config, "ROLLBACK", command.version());
        MockEndpoint saved = endpoints.saveRevision(current, command.revision());
        recordOperation(
                "ENDPOINT",
                id,
                "ROLLBACK",
                "从 v" + command.version() + " 回滚生成 v" + saved.getPublishedVersion(),
                address);
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockEndpoint lifecycle(Long id, Commands.Lifecycle command, String address) {
        MockEndpoint current = endpoints.loadOrThrow(id);
        systems.lockOrThrow(current.getSystemId());
        current = endpoints.loadOrThrow(id);
        assertRevision(current, command.revision());
        current.setArchived(command.archived());
        // 恢复接口时清空发布指针，避免恢复动作意外重新开放旧接口
        if (!command.archived()) current.setPublishedJson(null);
        current.setPublished(null);
        MockEndpoint saved = endpoints.saveRevision(current, command.revision());
        recordOperation(
                "ENDPOINT", id, command.archived() ? "ARCHIVE" : "RESTORE", "接口归档状态改变", address);
        return saved;
    }

    /**
     * 获得命名空间锁后重新读取接口，避免事务等待导致旧读
     *
     * @param id 接口主键
     * @param revision 期望修订号
     * @return 可编辑接口
     */
    private MockEndpoint loadEditableOrThrow(Long id, long revision) {
        MockEndpoint found = endpoints.loadOrThrow(id);
        MockSystem system = systems.lockOrThrow(found.getSystemId());
        assertWritableSystem(system);
        // 等待系统命名空间锁后重新读取，结合 READ_COMMITTED 避免基于旧配置写入
        MockEndpoint current = endpoints.loadOrThrow(id);
        assertRevision(current, revision);
        if (current.getArchived()) throw new BusinessException(400, "ARCHIVED", "请先恢复归档接口");
        return current;
    }

    /**
     * 防止基于陈旧页面覆盖他人配置
     *
     * @param current 当前接口
     * @param revision 页面携带的修订号
     */
    private void assertRevision(MockEndpoint current, long revision) {
        if (current.getRevision() != revision)
            throw new BusinessException(409, "REVISION_CONFLICT", "接口已被修改，请保留本地内容并重新加载");
    }

    /**
     * 已归档系统只允许恢复，不能继续写入接口
     *
     * @param system 当前系统
     */
    private void assertWritableSystem(MockSystem system) {
        if (system.getArchived()) throw new BusinessException(400, "ARCHIVED", "请先恢复第三方系统");
    }

    /**
     * 保存和发布同时保留新旧路由，避免回滚或切换时歧义
     *
     * @param systemId 命名空间
     * @param excludedId 当前接口，可为空
     * @param config 本次配置
     */
    private void assertRouteAvailable(Long systemId, Long excludedId, InterfaceConfig config) {
        List<MockEndpoint> candidates = endpoints.findBySystem(systemId);
        boolean conflict =
                candidates.stream()
                        .filter(e -> !e.getArchived())
                        .filter(e -> !Objects.equals(e.getId(), excludedId))
                        .anyMatch(
                                e ->
                                        sameRoute(e.getDraft(), config)
                                                || sameRoute(e.getPublished(), config));
        if (conflict) throw new BusinessException(409, "ROUTE_CONFLICT", "同一系统存在相同方法和路径的草稿或发布接口");
    }

    /**
     * 比较参数名称无关的路由形状
     *
     * @param a 已有配置
     * @param b 待校验配置
     * @return 是否占用同一路由
     */
    private boolean sameRoute(InterfaceConfig a, InterfaceConfig b) {
        return a != null
                && a.getMethod().equals(b.getMethod())
                && routes.shape(a.getPath()).equals(routes.shape(b.getPath()));
    }

    /**
     * 发布阶段执行完整校验，包含未运行脚本的语法检查
     *
     * @param endpoint 当前接口
     * @param config 待发布快照
     */
    private void assertPublishable(MockEndpoint endpoint, InterfaceConfig config) {
        validator.validate(config, true);
        assertRouteAvailable(endpoint.getSystemId(), endpoint.getId(), config);
    }

    /**
     * 初始化仅由服务端管理的状态
     *
     * @param input 客户端输入
     * @return 未发布接口
     */
    private MockEndpoint assembleEndpoint(MockEndpoint input) {
        MockEndpoint created = new MockEndpoint();
        created.setSystemId(input.getSystemId());
        created.setDraft(input.getDraft());
        created.setDraftJson(json.write(input.getDraft()));
        created.setCreatedAt(DbTime.now());
        created.setUpdatedAt(created.getCreatedAt());
        return created;
    }

    /**
     * 在外层公开事务中写入完整不可变快照并切换指针
     *
     * @param endpoint 当前接口
     * @param config 已校验配置
     * @param action 发布或回滚
     * @param sourceVersion 回滚来源，可为空
     */
    private void activateSnapshot(
            MockEndpoint endpoint, InterfaceConfig config, String action, Integer sourceVersion) {
        MockVersion snapshot = new MockVersion();
        snapshot.setEndpointId(endpoint.getId());
        snapshot.setVersion(endpoint.getPublishedVersion() + 1);
        snapshot.setConfigJson(json.write(config));
        snapshot.setAction(action);
        snapshot.setSourceVersion(sourceVersion);
        snapshot.setCreatedAt(DbTime.now());
        versions.save(snapshot);
        endpoint.setPublishedVersion(snapshot.getVersion());
        endpoint.setPublishedJson(snapshot.getConfigJson());
        endpoint.setPublished(config);
    }

    /**
     * 记录未认证操作的来源，不伪造操作人身份
     *
     * @param type 业务对象类型
     * @param id 对象主键
     * @param action 操作类型
     * @param detail 影响说明
     * @param address 来源地址
     */
    private void recordOperation(
            String type, Long id, String action, String detail, String address) {
        MockOperation operation = new MockOperation();
        operation.setTargetType(type);
        operation.setTargetId(id);
        operation.setAction(action);
        operation.setDetail(detail);
        operation.setSourceAddress(address);
        operation.setCreatedAt(DbTime.now());
        operations.save(operation);
    }
}
