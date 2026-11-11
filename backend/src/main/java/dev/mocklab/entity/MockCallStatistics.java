package dev.mocklab.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 独立保留真实调用的系统年度计数，不随调用日志清理
 */
@Data
@TableName("mock_call_statistics")
public class MockCallStatistics {
    /**
     * 统计记录主键
     */
    private Long id;
    /**
     * 匹配系统的主键，零表示未匹配到系统的真实调用
     */
    private Long systemId;
    /**
     * 请求到达时所属的 UTC 自然年
     */
    private Integer statYear;
    /**
     * 该系统该年的真实调用次数，包含失败响应，不包含管理端调试
     */
    private Long callCount;
}
