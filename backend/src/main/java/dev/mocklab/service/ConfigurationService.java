package dev.mocklab.service;

import dev.mocklab.entity.MockEndpoint;
import dev.mocklab.entity.MockSystem;
import dev.mocklab.model.Commands;

/**
 * 编排系统、接口、版本和操作记录的跨表写入，并统一管理事务边界
 */
public interface ConfigurationService {
    /**
     * 保存系统并记录关键操作
     *
     * @param input 系统设置
     * @param address 可信来源地址
     * @return 保存后的系统
     */
    MockSystem saveSystem(MockSystem input, String address);

    /**
     * 在系统内创建未发布接口
     *
     * @param input 包含 systemId 和 draft 的接口
     * @param address 来源地址
     * @return 新接口
     */
    MockEndpoint create(MockEndpoint input, String address);

    /**
     * 保存接口草稿，不改变发布状态
     *
     * @param id 接口主键
     * @param command 草稿及预期修订号
     * @return 更新后的接口
     */
    MockEndpoint saveDraft(Long id, Commands.Save command);

    /**
     * 发布本次配置并原子切换版本
     *
     * @param id 接口主键
     * @param command 本次确认的配置及修订号
     * @param address 来源地址
     * @return 发布后的接口
     */
    MockEndpoint publish(Long id, Commands.Save command, String address);

    /**
     * 从历史配置创建新的发布版本，保持草稿
     *
     * @param id 接口主键
     * @param command 版本和并发检查信息
     * @param address 来源地址
     * @return 回滚后的接口
     */
    MockEndpoint rollback(Long id, Commands.Rollback command, String address);

    /**
     * 归档或恢复接口，恢复时仍需重新发布
     *
     * @param id 接口主键
     * @param command 目标状态和预期修订号
     * @param address 来源地址
     * @return 更新后的接口
     */
    MockEndpoint lifecycle(Long id, Commands.Lifecycle command, String address);
}
