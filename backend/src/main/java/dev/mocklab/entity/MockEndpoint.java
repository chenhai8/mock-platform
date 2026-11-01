package dev.mocklab.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import dev.mocklab.model.InterfaceConfig;

import lombok.Data;

/**
 * 保存接口草稿、当前发布快照及并发修订信息
 */
@Data
@TableName("mock_endpoint")
public class MockEndpoint {
    /**
     * 接口数据库主键
     */
    private Long id;
    /**
     * 所属第三方系统主键
     */
    private Long systemId;
    /**
     * 数据库中的草稿 JSON，接口输出使用解析后的 draft
     */
    @JsonIgnore private String draftJson;

    /**
     * 当前生效快照，允许显式更新为 null 以清除发布指针
     */
    @JsonIgnore
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String publishedJson;

    /**
     * 草稿与生命周期操作共用的并发修订号，每次成功更新递增
     */
    private Long revision = 0L;
    /**
     * 已生成的最新发布版本号，归档恢复不会重置历史编号
     */
    private Integer publishedVersion = 0;
    /**
     * 接口归档状态，恢复后仍需重新发布才能被真实请求调用
     */
    private Boolean archived = false;

    /**
     * 供管理端编辑的解析配置，不直接映射数据库列
     */
    @TableField(exist = false)
    private InterfaceConfig draft;

    /**
     * 供路由执行和页面查看的发布快照，不直接映射数据库列
     */
    @TableField(exist = false)
    private InterfaceConfig published;

    /**
     * 创建时间，固定毫秒精度的 UTC 字符串
     */
    private String createdAt;
    /**
     * 最近修改时间，固定毫秒精度的 UTC 字符串
     */
    private String updatedAt;
}
