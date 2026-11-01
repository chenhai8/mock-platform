package dev.mocklab.engine;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 根据 runner 进程池状态提供健康指标，不执行用户脚本
 */
@Component("scriptRunner")
@RequiredArgsConstructor
public class RunnerHealthIndicator implements HealthIndicator {
    private final RunnerClient runner;

    @Override
    public Health health() {
        try {
            Map<String, Object> state = runner.invoke(Map.of("operation", "ping"));
            if (((Number) state.getOrDefault("ready", 0)).intValue() == 0)
                return Health.down().build();
            return Health.up().withDetails(state).build();
        } catch (Exception error) {
            return Health.down().withDetail("reason", "runner unavailable").build();
        }
    }
}
