package dev.mocklab.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import dev.mocklab.config.MockProperties;
import dev.mocklab.entity.MockCallLog;
import dev.mocklab.entity.MockEndpoint;
import dev.mocklab.entity.MockOperation;
import dev.mocklab.entity.MockSystem;
import dev.mocklab.entity.MockVersion;
import dev.mocklab.model.Commands;
import dev.mocklab.model.MockResult;
import dev.mocklab.service.CallLogService;
import dev.mocklab.service.ConfigurationService;
import dev.mocklab.service.DispatchService;
import dev.mocklab.service.EndpointService;
import dev.mocklab.service.OperationService;
import dev.mocklab.service.SystemService;
import dev.mocklab.service.VersionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 接收管理请求并交给领域或编排 Service，保持 HTTP 入口无业务逻辑
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ManagementController {
    private final SystemService systems;
    private final EndpointService endpoints;
    private final ConfigurationService configuration;
    private final VersionService versions;
    private final DispatchService dispatch;
    private final CallLogService logs;
    private final OperationService operations;
    private final MockProperties properties;

    /**
     * 返回管理页面需要展示的平台限制和调用地址配置
     *
     * @return 当前平台配置
     */
    @GetMapping("/settings")
    public MockProperties settings() {
        return properties;
    }

    /**
     * 按归档状态查询第三方系统，业务筛选由系统 Service 处理
     *
     * @param archived 是否查看已归档系统
     * @return 符合归档状态的系统列表
     */
    @GetMapping("/systems")
    public List<MockSystem> systems(@RequestParam(defaultValue = "false") boolean archived) {
        return systems.findSystems(archived);
    }

    /**
     * 接收系统设置并交由跨表编排服务保存和记录操作
     *
     * @param input 经过 Bean Validation 的系统设置
     * @param request 用于取得直接连接来源地址的请求
     * @return 保存后的系统
     */
    @PostMapping(value = "/systems", consumes = "application/json")
    public MockSystem saveSystem(@Valid @RequestBody MockSystem input, HttpServletRequest request) {
        return configuration.saveSystem(input, request.getRemoteAddr());
    }

    /**
     * 查询一个第三方系统下的接口配置
     *
     * @param id 系统主键
     * @return 包含草稿和发布状态的接口列表
     */
    @GetMapping("/systems/{id}/endpoints")
    public List<MockEndpoint> endpoints(@PathVariable Long id) {
        return endpoints.findBySystem(id);
    }

    /**
     * 提交新接口的系统归属和草稿，服务端初始化发布状态
     *
     * @param input 包含 systemId 与 draft 的接口输入
     * @param request 用于记录操作来源的请求
     * @return 新建的未发布接口
     */
    @PostMapping(value = "/endpoints", consumes = "application/json")
    public MockEndpoint create(@RequestBody MockEndpoint input, HttpServletRequest request) {
        return configuration.create(input, request.getRemoteAddr());
    }

    /**
     * 读取单个接口及其解析后的配置
     *
     * @param id 接口主键
     * @return 接口详情
     */
    @GetMapping("/endpoints/{id}")
    public MockEndpoint endpoint(@PathVariable Long id) {
        return endpoints.loadOrThrow(id);
    }

    /**
     * 保存本次编辑的草稿，保持当前发布快照
     *
     * @param id 接口主键
     * @param input 完整配置及预期修订号
     * @return 修订号更新后的接口
     */
    @PutMapping(value = "/endpoints/{id}/draft", consumes = "application/json")
    public MockEndpoint save(@PathVariable Long id, @Valid @RequestBody Commands.Save input) {
        return configuration.saveDraft(id, input);
    }

    /**
     * 发布用户本次确认的配置，具体校验和版本切换在服务事务中完成
     *
     * @param id 接口主键
     * @param input 待发布配置及预期修订号
     * @param request 用于记录操作来源的请求
     * @return 发布后的接口
     */
    @PostMapping(value = "/endpoints/{id}/publish", consumes = "application/json")
    public MockEndpoint publish(
            @PathVariable Long id,
            @Valid @RequestBody Commands.Save input,
            HttpServletRequest request) {
        return configuration.publish(id, input, request.getRemoteAddr());
    }

    /**
     * 提交历史版本回滚命令，服务端保留草稿并生成新发布版本
     *
     * @param id 接口主键
     * @param input 历史版本及当前修订检查信息
     * @param request 用于记录操作来源的请求
     * @return 回滚后的接口
     */
    @PostMapping(value = "/endpoints/{id}/rollback", consumes = "application/json")
    public MockEndpoint rollback(
            @PathVariable Long id,
            @Valid @RequestBody Commands.Rollback input,
            HttpServletRequest request) {
        return configuration.rollback(id, input, request.getRemoteAddr());
    }

    /**
     * 提交接口归档或恢复命令，状态边界由编排服务校验
     *
     * @param id 接口主键
     * @param input 目标归档状态及预期修订号
     * @param request 用于记录操作来源的请求
     * @return 更新后的接口
     */
    @PostMapping(value = "/endpoints/{id}/lifecycle", consumes = "application/json")
    public MockEndpoint lifecycle(
            @PathVariable Long id,
            @Valid @RequestBody Commands.Lifecycle input,
            HttpServletRequest request) {
        return configuration.lifecycle(id, input, request.getRemoteAddr());
    }

    /**
     * 查询接口不可变发布历史
     *
     * @param id 接口主键
     * @return 按发布版本倒序排列的历史快照
     */
    @GetMapping("/endpoints/{id}/versions")
    public List<MockVersion> history(@PathVariable Long id) {
        return versions.history(id);
    }

    /**
     * 将调试输入交给统一执行链路，响应中包含场景和耗时诊断
     *
     * @param id 接口主键
     * @param input 调试来源、配置及模拟请求
     * @param request 用于记录调用来源的请求
     * @return 完成延迟后返回的调试结果
     */
    @PostMapping(value = "/endpoints/{id}/debug", consumes = "application/json")
    public CompletableFuture<MockResult> debug(
            @PathVariable Long id,
            @Valid @RequestBody Commands.Debug input,
            HttpServletRequest request) {
        return dispatch.debug(id, input, request.getRemoteAddr());
    }

    /**
     * 接收调用日志的分页和过滤条件，查询及容量限制由日志服务处理
     *
     * @param page 分页参数
     * @param filter 系统、接口及执行结果筛选
     * @param from 起始 UTC 时间，可为空
     * @param to 结束 UTC 时间，可为空
     * @return 调用日志分页结果
     */
    @GetMapping("/logs")
    public Page<MockCallLog> logs(
            Page<MockCallLog> page,
            @ModelAttribute MockCallLog filter,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return logs.search(page, filter, from, to);
    }

    /**
     * 读取一条调用日志及已启用的诊断详情
     *
     * @param id 调用记录主键
     * @return 调用详情，不存在时为 null
     */
    @GetMapping("/logs/{id}")
    public MockCallLog log(@PathVariable Long id) {
        return logs.getById(id);
    }

    /**
     * 读取近期关键配置操作记录
     *
     * @return 最近一百条操作记录
     */
    @GetMapping("/operations")
    public List<MockOperation> operations() {
        return operations.recent();
    }
}
