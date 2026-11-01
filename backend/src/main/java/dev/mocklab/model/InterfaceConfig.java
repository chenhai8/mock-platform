package dev.mocklab.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 可保存为草稿或不可变发布快照的接口配置，场景按优先级选择
 */
@Data
public class InterfaceConfig {
    /**
     * 接口展示名称，不参与请求路径匹配
     */
    private String name;
    /**
     * 接口用途说明，可为空字符串
     */
    private String description = "";
    /**
     * 允许调用的 HTTP 方法，一个配置仅对应一种方法
     */
    private String method = "POST";
    /**
     * 系统命名空间内的接口路径，支持形如 {orderId} 的单段参数
     */
    private String path;
    /**
     * 接口发布快照中的开关，停用后不参与真实路由匹配
     */
    private boolean enabled = true;
    /**
     * 响应场景集合，按优先级选择且必须包含一个默认场景
     */
    private List<Scene> rules = new ArrayList<>();
}
