package dev.mocklab.service;

import com.baomidou.mybatisplus.extension.service.IService;

import dev.mocklab.common.BusinessException;
import dev.mocklab.entity.MockSystem;

import java.util.List;

/**
 * 管理第三方系统及其命名空间状态，集中维护系统表查询条件
 */
public interface SystemService extends IService<MockSystem> {
    /**
     * 列出指定归档状态的系统
     *
     * @param archived 是否归档
     * @return 系统列表
     */
    List<MockSystem> findSystems(boolean archived);

    /**
     * 根据标识读取可调用系统
     *
     * @param key 系统标识
     * @return 可调用系统
     * @throws BusinessException 系统不可用
     */
    MockSystem loadActiveOrThrow(String key);

    /**
     * 获取系统并锁定路由命名空间，调用方必须开启事务
     *
     * @param id 系统主键
     * @return 当前系统
     */
    MockSystem lockOrThrow(Long id);

    /**
     * 保存系统设置并检查修订号和不可变标识
     *
     * @param system 输入系统
     * @return 保存后的系统
     */
    MockSystem saveSettings(MockSystem system);
}
