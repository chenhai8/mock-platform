package dev.mocklab.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import dev.mocklab.common.BusinessException;
import dev.mocklab.common.DbTime;
import dev.mocklab.entity.MockSystem;
import dev.mocklab.mapper.MockSystemMapper;
import dev.mocklab.service.SystemService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 维护系统标识、总开关和修订号，向编排层提供命名空间锁
 */
@Service
public class SystemServiceImpl extends ServiceImpl<MockSystemMapper, MockSystem>
        implements SystemService {
    @Override
    public List<MockSystem> findSystems(boolean archived) {
        return lambdaQuery()
                .eq(MockSystem::getArchived, archived)
                .orderByAsc(MockSystem::getId)
                .list();
    }

    @Override
    public MockSystem loadActiveOrThrow(String key) {
        MockSystem system =
                lambdaQuery()
                        .eq(MockSystem::getSystemKey, key)
                        .eq(MockSystem::getEnabled, true)
                        .eq(MockSystem::getArchived, false)
                        .one();
        if (system == null) throw new BusinessException(404, "MOCK_NOT_FOUND", "系统或接口不可用");
        return system;
    }

    @Override
    public MockSystem lockOrThrow(Long id) {
        MockSystem system = lambdaQuery().eq(MockSystem::getId, id).last("FOR UPDATE").one();
        if (system == null) throw new BusinessException(404, "NOT_FOUND", "系统不存在");
        return system;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MockSystem saveSettings(MockSystem system) {
        if (system.getId() == null) return createSystem(system);
        MockSystem previous = lockOrThrow(system.getId());
        assertSettings(previous, system);
        // 恢复系统仍保持停用，必须由使用者明确开启后才接受真实调用
        if (previous.getArchived() && !system.getArchived()) system.setEnabled(false);
        system.setRevision(previous.getRevision() + 1);
        system.setCreatedAt(previous.getCreatedAt());
        system.setUpdatedAt(DbTime.now());
        updateById(system);
        return system;
    }

    /**
     * 初始化系统且禁止客户端指定服务端状态
     *
     * @param input 合法系统配置
     * @return 创建后的系统
     */
    private MockSystem createSystem(MockSystem input) {
        if (lambdaQuery().eq(MockSystem::getSystemKey, input.getSystemKey()).exists())
            throw new BusinessException(409, "SYSTEM_KEY_EXISTS", "系统标识已存在");
        input.setId(null);
        input.setName(input.getName().trim());
        input.setRevision(0L);
        input.setArchived(false);
        input.setCreatedAt(DbTime.now());
        input.setUpdatedAt(input.getCreatedAt());
        save(input);
        return input;
    }

    /**
     * 阻止覆盖他人的修改或变更系统命名空间
     *
     * @param previous 数据库当前配置
     * @param input 待保存配置
     */
    private void assertSettings(MockSystem previous, MockSystem input) {
        if (!Objects.equals(previous.getRevision(), input.getRevision()))
            throw new BusinessException(409, "REVISION_CONFLICT", "配置已被修改，请保留本地内容并重新加载");
        if (!Objects.equals(previous.getSystemKey(), input.getSystemKey()))
            throw new BusinessException(400, "IMMUTABLE_KEY", "系统标识创建后不可修改");
        if (input.getEnabled() == null || input.getArchived() == null)
            throw new BusinessException(400, "INVALID_CONFIG", "系统状态不能为空");
    }
}
