package dev.mocklab.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.Data;

/**
 * 顶部调用概览，累计范围从独立统计功能启用时开始，次数以字符串传输避免前端整数精度丢失
 */
@Data
public class CallStatisticsSummary {
    /**
     * 本次查询使用的 UTC 自然年
     */
    private int statYear;
    /**
     * 所有系统及未匹配系统请求的历年累计真实调用次数
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private long totalCalls;
    /**
     * 所有系统及未匹配系统请求在本年的真实调用次数
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private long yearCalls;
    /**
     * 当前系统在本年的真实调用次数，未选择系统时为零
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private long systemYearCalls;
}
