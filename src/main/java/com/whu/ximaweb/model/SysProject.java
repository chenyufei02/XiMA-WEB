package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_project")
public class SysProject {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String projectName;
    // 大疆配置
    private String djiProjectUuid;
    private String djiOrgKey;
    // 华为云配置
    private String obsBucketName;
    private String obsAk;
    private String obsSk;
    private String obsEndpoint;

    private Integer createdBy;
    private LocalDateTime createdAt;
}