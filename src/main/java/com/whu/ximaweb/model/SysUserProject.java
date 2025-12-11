package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_user_project")
public class SysUserProject {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer projectId;
}