package dev.mocklab;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动管理接口、Mock HTTP 入口及日志定时任务，脚本进程由独立 runner 管理
 */
@EnableScheduling
@MapperScan("dev.mocklab.mapper")
@ConfigurationPropertiesScan
@SpringBootApplication
public class MockLabApplication {
    /**
     * 启动 Spring Boot 管理与 Mock HTTP 服务，runner 需要独立启动
     *
     * @param args Spring Boot 命令行配置参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MockLabApplication.class, args);
    }
}
