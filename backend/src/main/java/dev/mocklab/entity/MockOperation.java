package dev.mocklab.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 记录配置关键操作及来源地址，免登录模式不虚构操作人身份
 */
@Data
@TableName("mock_operation")
public class MockOperation {
    /**
     * 操作记录数据库主键
     */
    private Long id;
    /**
     * 操作对象类型：SYSTEM 或 ENDPOINT
     */
    private String targetType;
    /**
     * 操作对象主键
     */
    private Long targetId;
    /**
     * 关键操作编码，如发布、回滚、归档或恢复
     */
    private String action;
    /**
     * 操作结果说明，不包含脚本或报文全文
     */
    private String detail;
    /**
     * 后端观察到的请求来源地址，不代表经过认证的用户身份
     */
    private String sourceAddress;
    /**
     * 操作记录时间，固定毫秒精度的 UTC 字符串
     */
    private String createdAt;
}
