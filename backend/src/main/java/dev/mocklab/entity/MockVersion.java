package dev.mocklab.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.mocklab.model.InterfaceConfig;

import lombok.Data;

/**
 * 接口的一次不可变发布快照，回滚会生成新版本而非覆盖历史
 */
@Data
@TableName("mock_version")
public class MockVersion {
    /**
     * 历史快照数据库主键
     */
    private Long id;
    /**
     * 所属接口主键
     */
    private Long endpointId;
    /**
     * 接口内递增且唯一的发布版本号
     */
    private Integer version;
    /**
     * 不可变发布配置 JSON，接口输出使用解析后的 config
     */
    @JsonIgnore private String configJson;
    /**
     * 版本产生方式：PUBLISH 或 ROLLBACK
     */
    private String action;
    /**
     * 回滚来源版本，正常发布时为空
     */
    private Integer sourceVersion;

    /**
     * 解析后的历史配置，不直接映射数据库列
     */
    @TableField(exist = false)
    private InterfaceConfig config;

    /**
     * 版本生成时间，固定毫秒精度的 UTC 字符串
     */
    private String createdAt;
}
