package com.whu.ximaweb.dto.dji;

import lombok.Data;

@Data
public class DjiProjectDto {
    private String workspace_id; // 对应大疆的 workspace_id
    private String name;
    private String description;

    // 我们自己约定的字段，方便前端取用
    private String uuid; // 通常就是 workspace_id

    /**
     * ✅ 新增字段：标记当前用户是否已导入该项目
     * true = 已导入 (前端置灰)
     * false = 未导入 (前端可选)
     */
    private boolean isImported = false;
}