package dev.mocklab.engine;

import dev.mocklab.common.BusinessException;
import dev.mocklab.config.MockProperties;
import dev.mocklab.model.MockResult;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 在响应生成后异步等待配置延迟，避免占用脚本工作进程
 */
@Component
public class DelayScheduler {
    private final Semaphore capacity;
    private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(2);

    /**
     * 按平台配置建立延迟容量门槛，防止长延迟请求无限积压
     *
     * @param properties 包含延迟等待容量的平台设置
     */
    public DelayScheduler(MockProperties properties) {
        capacity = new Semaphore(properties.getDelayCapacity());
    }

    /**
     * 生成响应后释放脚本资源，再在有限容量的定时器中等待额外延迟
     *
     * @param result 已生成且包含延迟毫秒数的响应
     * @return 延迟结束后完成的响应，零延迟立即完成
     */
    public CompletableFuture<MockResult> schedule(MockResult result) {
        if (result.getDelayMs() == 0) return CompletableFuture.completedFuture(result);
        if (!capacity.tryAcquire())
            throw new BusinessException(503, "MOCK_SERVICE_UNAVAILABLE", "延迟等待容量已满");
        CompletableFuture<MockResult> future = new CompletableFuture<>();
        timer.schedule(
                () -> {
                    capacity.release();
                    future.complete(result);
                },
                result.getDelayMs(),
                TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * 服务关闭时取消尚未触发的延迟任务，停止定时器线程
     */
    @PreDestroy
    public void stop() {
        timer.shutdownNow();
    }
}
