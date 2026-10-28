package dev.mocklab.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 第三方系统配置，以不可变 systemKey 划分 Mock 路由命名空间
 */
@Data
@TableName("mock_system")
public class MockSystem {
    /**
     * 系统数据库主键，新建时由数据库生成
     */
    private Long id;

    /**
     * 第三方系统展示名称
     */
    @NotBlank
    @Size(max = 50)
    private String name;

    /**
     * 创建后不可修改的路由命名空间，仅允许小写字母开头及小写字母、数字、短横线
     */
    @NotBlank
    @Pattern(regexp = "[a-z][a-z0-9-]{0,39}")
    private String systemKey;

    /**
     * 系统用途说明
     */
    @Size(max = 500)
    private String description = "";

    /**
     * 展示排序值，非负且越小越靠前，相同值按主键升序排列，未指定时由数据库默认为 0
     */
    @Min(0)
    private Integer sortOrder;

    /**
     * 系统总开关，停用后所有真实调用及发布调试不可用
     */
    @NotNull private Boolean enabled = true;
    /**
     * 系统归档标记，归档后禁止调用及继续编辑接口
     */
    @NotNull private Boolean archived = false;
    /**
     * 系统设置修订号，用于阻止陈旧页面覆盖最新配置
     */
    @NotNull private Long revision = 0L;
    /**
     * 创建时间，固定毫秒精度的 UTC 字符串
     */
    private String createdAt;
    /**
     * 最近修改时间，固定毫秒精度的 UTC 字符串
     */
    private String updatedAt;
}
