package com.whu.ximaweb.service;

/**
 * 进度管理服务的接口。
 * 这个接口定义了所有与进度相关的业务逻辑“契约”。
 * 比如，“刷新实际进度数据”这个功能，我们就先在这里定义好。
 */
public interface ProgressService {

    /**
     * 这是我们业务逻辑层的第一个核心方法：刷新实际进度数据。
     * 它将负责完成从读取照片到存入数据库的整个流水线工作。
     */
    void refreshActualProgress();

    // 未来我们还可以在这里添加更多业务方法，比如：
    // List<ProgressDataPoint> getPlanProgress();
    // List<ProgressDataPoint> getActualProgress();
}