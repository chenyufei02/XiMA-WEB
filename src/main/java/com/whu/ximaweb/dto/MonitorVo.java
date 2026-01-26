package com.whu.ximaweb.dto;

import lombok.Data;
import java.util.List;

@Data
public class MonitorVo {
    // === 左侧：司空2平台监控 ===
    private boolean djiConnected;     // 是否连接正常
    private Long totalPhotos;         // 累计同步照片数
    private String lastSyncTime;      // 上次同步时间 (字符串格式)
    private String nextSyncTime;      // 下次同步预计时间 (倒计时用)
    private Long deviceCount;         // (备用) 设备数，目前没表，暂存0

    // === 右侧：日报邮件监控 ===
    private boolean reportEnabled;    // 日报是否开启
    private Long runDays;             // 系统安全运行天数
    private Integer totalReports;     // 累计发送日报次数
    private String lastReportTime;    // 上次发送时间
    private String nextReportTime;    // 下次发送预计时间
    private String receiverName;      // 接收人名字

    // === 底部：日志流 ===
    private List<LogItem> logs;       // 滚动日志列表

    @Data
    public static class LogItem {
        private String time;          // "10:00:01"
        private String message;       // "同步成功..."
        private String type;          // "INFO", "SUCCESS", "ERROR"
    }
}