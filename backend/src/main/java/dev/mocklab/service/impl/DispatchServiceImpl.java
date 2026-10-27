package dev.mocklab.service.impl;

import dev.mocklab.common.BusinessException;
import dev.mocklab.engine.ConfigValidator;
import dev.mocklab.engine.DelayScheduler;
import dev.mocklab.engine.RequestParser;
import dev.mocklab.engine.ResponseEngine;
import dev.mocklab.engine.ResultFactory;
import dev.mocklab.engine.RouteMatcher;
import dev.mocklab.engine.SceneMatcher;
import dev.mocklab.entity.MockEndpoint;
import dev.mocklab.entity.MockSystem;
import dev.mocklab.logging.LogWriter;
import dev.mocklab.model.Commands;
import dev.mocklab.model.Exchange;
import dev.mocklab.model.InterfaceConfig;
import dev.mocklab.model.MockResult;
import dev.mocklab.model.Scene;
import dev.mocklab.service.DispatchService;
import dev.mocklab.service.EndpointService;
import dev.mocklab.service.SystemService;

import jakarta.servlet.http.HttpServletRequest;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 捕获本次调用的配置并完成路由、场景、脚本、延迟与日志编排
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {
    private final SystemService systems;
    private final EndpointService endpoints;
    private final RouteMatcher routes;
    private final RequestParser parser;
    private final SceneMatcher scenes;
    private final ConfigValidator validator;
    private final ResponseEngine engine;
    private final ResultFactory results;
    private final DelayScheduler delays;
    private final LogWriter logs;

    @Override
    public CompletableFuture<ResponseEntity<byte[]>> dispatch(HttpServletRequest request) {
        Call call = new Call(request.getMethod(), "LIVE", request.getRemoteAddr());
        try {
            prepareLive(call, request);
        } catch (BusinessException error) {
            call.setError(error);
        } catch (Exception error) {
            call.setError(unavailable(error));
        }
        return execute(call).thenApply(result -> httpResponse(result, request.getMethod()));
    }

    @Override
    public CompletableFuture<MockResult> debug(Long id, Commands.Debug command, String address) {
        Call call =
                new Call(
                        command.method(),
                        "published".equals(command.source()) ? "PUBLISHED_DEBUG" : "DRAFT_DEBUG",
                        address);
        try {
            prepareDebug(call, id, command);
        } catch (BusinessException error) {
            call.setError(error);
        } catch (Exception error) {
            call.setError(unavailable(error));
        }
        return execute(call);
    }

    /**
     * 捕获系统及接口的当前发布配置，再读取请求正文
     *
     * @param call 本次调用状态
     * @param request 原始请求
     */
    private void prepareLive(Call call, HttpServletRequest request) {
        String[] uri = request.getRequestURI().split("/", 4);
        if (uri.length < 4) throw new BusinessException(404, "MOCK_NOT_FOUND", "系统或接口不可用");
        call.setSystem(systems.loadActiveOrThrow(uri[2]));
        String path = "/" + uri[3];
        routes.assertRequestPath(path);
        call.setEndpoint(selectEndpoint(call.getSystem().getId(), path, request.getMethod()));
        call.setConfig(call.getEndpoint().getPublished());
        call.setVersion(call.getEndpoint().getPublishedVersion());
        call.setExchange(parser.fromServlet(request, path));
    }

    /**
     * 调试输入按真实入口格式解析，发布调试不信任客户端配置
     *
     * @param call 本次调用状态
     * @param id 当前接口
     * @param command 页面输入
     */
    private void prepareDebug(Call call, Long id, Commands.Debug command) {
        MockEndpoint endpoint = endpoints.loadOrThrow(id);
        call.setEndpoint(endpoint);
        MockSystem system = systems.getById(endpoint.getSystemId());
        call.setSystem(system);
        boolean published = "published".equals(command.source());
        InterfaceConfig config = published ? endpoint.getPublished() : command.config();
        assertDebugAvailable(system, endpoint, config, published);
        validator.validate(config, false);
        routes.assertRequestPath(command.path());
        call.setConfig(config);
        call.setVersion(
                published
                        ? endpoint.getPublishedVersion()
                        : command.revision() == null ? 0 : command.revision());
        call.setExchange(parseDebugInput(command));
    }

    /**
     * 将调试表单转换为与真实请求相同的上下文
     *
     * @param command 调试输入
     * @return 已解析的请求上下文
     */
    private Exchange parseDebugInput(Commands.Debug command) {
        byte[] body =
                (command.rawBody() == null ? "" : command.rawBody())
                        .getBytes(StandardCharsets.UTF_8);
        return parser.parse(
                command.method(), command.path(), command.query(), command.headers(), body);
    }

    /**
     * 已发布调试遵循总开关，草稿允许在停用系统内试运行
     *
     * @param system 当前系统
     * @param endpoint 当前接口
     * @param config 待调试配置
     * @param published 是否使用已发布版本
     */
    private void assertDebugAvailable(
            MockSystem system, MockEndpoint endpoint, InterfaceConfig config, boolean published) {
        if (system == null
                || system.getArchived()
                || endpoint.getArchived()
                || config == null
                || published && !system.getEnabled())
            throw new BusinessException(404, "MOCK_NOT_FOUND", "该接口配置不可调试");
    }

    /**
     * 路径优先于方法，防止静态路由的方法错误回退到参数路由
     *
     * @param systemId 系统主键
     * @param path 请求路径
     * @param method 请求方法
     * @return 匹配接口
     */
    private MockEndpoint selectEndpoint(Long systemId, String path, String method) {
        List<MockEndpoint> matches = findMatchingEndpoints(systemId, path);
        if (CollectionUtils.isEmpty(matches))
            throw new BusinessException(404, "MOCK_NOT_FOUND", "系统或接口不可用");
        // 先选最具体路径形状再匹配 HTTP 方法，静态路径不因方法错误回退到参数接口
        String shape = routes.shape(matches.getFirst().getPublished().getPath());
        List<MockEndpoint> best =
                matches.stream()
                        .filter(e -> routes.shape(e.getPublished().getPath()).equals(shape))
                        .toList();
        return selectMethodOrThrow(best, method);
    }

    /**
     * 仅匹配有效发布路由，并按逐段路径优先级排序
     *
     * @param systemId 系统命名空间
     * @param path 实际请求路径
     * @return 从具体到宽泛的候选路由
     */
    private List<MockEndpoint> findMatchingEndpoints(Long systemId, String path) {
        return endpoints.findBySystem(systemId).stream()
                .filter(e -> !e.getArchived() && e.getPublished() != null)
                .filter(e -> e.getPublished().isEnabled())
                .filter(e -> routes.parameters(e.getPublished().getPath(), path) != null)
                .sorted(
                        (a, b) ->
                                routes.compareSpecificity(
                                        a.getPublished().getPath(), b.getPublished().getPath()))
                .toList();
    }

    /**
     * 只在最具体路由内选择方法，并提供正确的 Allow 头
     *
     * @param candidates 同形状的最具体路由
     * @param method 请求方法
     * @return 匹配方法的接口
     */
    private MockEndpoint selectMethodOrThrow(List<MockEndpoint> candidates, String method) {
        String allowed =
                candidates.stream()
                        .map(e -> e.getPublished().getMethod())
                        .sorted()
                        .collect(Collectors.joining(", "));
        return candidates.stream()
                .filter(e -> e.getPublished().getMethod().equals(method))
                .findFirst()
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        405,
                                        "METHOD_NOT_ALLOWED",
                                        "该路径不支持此 HTTP 方法",
                                        Map.of("Allow", allowed)));
    }

    /**
     * 响应生成与延迟等待分离，延迟不占用脚本工作进程
     *
     * @param call 捕获的配置和请求
     * @return 完整执行结果
     */
    private CompletableFuture<MockResult> execute(Call call) {
        try {
            MockResult result =
                    call.getError() == null
                            ? generate(call)
                            : results.error(call.getError(), call.getTraceId(), call.isDebug());
            return delays.schedule(result).thenApply(completed -> complete(call, completed));
        } catch (BusinessException error) {
            return CompletableFuture.completedFuture(
                    complete(call, results.error(error, call.getTraceId(), call.isDebug())));
        } catch (Exception error) {
            return CompletableFuture.completedFuture(
                    complete(
                            call,
                            results.error(unavailable(error), call.getTraceId(), call.isDebug())));
        }
    }

    /**
     * 在指定配置内匹配场景并生成响应
     *
     * @param call 本次调用
     * @return 待延迟的响应
     */
    private MockResult generate(Call call) {
        InterfaceConfig config = call.getConfig();
        if (!config.isEnabled())
            throw new BusinessException(404, "MOCK_NOT_FOUND", "接口已停用");
        if (!config.getMethod().equals(call.getMethod()))
            throw new BusinessException(
                    405, "METHOD_NOT_ALLOWED", "请求方法不匹配", Map.of("Allow", config.getMethod()));
        Map<String, Object> parameters =
                routes.parameters(config.getPath(), call.getExchange().getPath());
        if (parameters == null) throw new BusinessException(404, "MOCK_NOT_FOUND", "请求路径未匹配当前接口");
        call.getExchange().setPathParams(parameters);
        Scene scene = scenes.select(config.getRules(), call.getExchange());
        call.setScene(scene.getName());
        return engine.execute(scene, call.getExchange());
    }

    /**
     * 补齐元信息并异步写日志，HEAD 只在输出阶段省略正文
     *
     * @param call 调用上下文
     * @param result 响应
     * @return 带追踪信息的结果
     */
    private MockResult complete(Call call, MockResult result) {
        result.setTraceId(call.getTraceId());
        result.setSource(call.getSource());
        result.setConfigVersion(call.getVersion());
        result.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - call.getStarted()));
        if (result.getScene() == null) result.setScene(call.getScene());
        Exchange exchange = call.getExchange();
        if (exchange == null) {
            exchange = new Exchange();
            exchange.setMethod(call.getMethod());
        }
        logs.record(
                call.getSystem() == null ? null : call.getSystem().getId(),
                call.getEndpoint() == null ? null : call.getEndpoint().getId(),
                call.getSystem() == null ? null : call.getSystem().getName(),
                call.getConfig() == null ? "[未匹配]" : call.getConfig().getPath(),
                call.getAddress(),
                exchange,
                result);
        // 先记录生成结果，再抑制 HEAD 正文，使日志仍能说明实际生成的响应大小
        if ("HEAD".equals(call.getMethod())) result.setBody("");
        return result;
    }

    /**
     * 将响应描述转换为 HTTP，不进行业务包装
     *
     * @param result 完整结果
     * @param method 请求方法
     * @return HTTP 响应
     */
    private ResponseEntity<byte[]> httpResponse(MockResult result, String method) {
        HttpHeaders headers = new HttpHeaders();
        result.getHeaders().forEach(headers::set);
        byte[] body =
                "HEAD".equals(method)
                        ? new byte[0]
                        : result.getBody().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.status(result.getStatus()).headers(headers).body(body);
    }

    /**
     * 记录平台内部异常，对外隐藏实现细节
     *
     * @param error 平台异常
     * @return 可公开的业务异常
     */
    private BusinessException unavailable(Exception error) {
        log.error("Mock dispatch unavailable", error);
        return new BusinessException(503, "MOCK_SERVICE_UNAVAILABLE", "Mock 服务暂不可用");
    }

    /**
     * 保存一次调用捕获的状态，避免配置更新影响已经开始执行的请求
     */
    @Data
    private static class Call {
        private final String method;
        private final String source;
        private final String address;
        private final long started = System.nanoTime();
        private final String traceId = UUID.randomUUID().toString();
        private MockSystem system;
        private MockEndpoint endpoint;
        private InterfaceConfig config;
        private Exchange exchange;
        private BusinessException error;
        private long version;
        private String scene;

        /**
         * 区分真实业务调用与管理调试，以决定错误详情是否可展示
         *
         * @return 非 LIVE 来源均视为管理调试
         */
        public boolean isDebug() {
            return !"LIVE".equals(source);
        }
    }
}
