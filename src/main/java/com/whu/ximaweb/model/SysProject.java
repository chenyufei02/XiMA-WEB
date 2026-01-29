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
    private String photoFolderKeyword;

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

    /**
     * 是否开启每日AI进度监察报告 (1=开启, 0=关闭)
     */
    private Integer enableAiReport;


    // ==========================================
    // 🔥 [新增] 萤石云摄像头配置字段
    // ==========================================

    /** 萤石云 AppKey */
    private String ezvizAppKey;
    /** 萤石云 Secret */
    private String ezvizAppSecret;
    /** 设备序列号 */
    private String ezvizDeviceSerial;
    /** 设备验证码 (视频加密时必填) */
    private String ezvizValidateCode;
    /** 自动获取的访问令牌 (前端播放需要) */
    private String ezvizAccessToken;
    /** 令牌过期时间 */
    private java.util.Date ezvizTokenExpireTime;

    // 注意：boundaryCoords 字段已被移除，现已迁移至 SysBuilding 表中
}