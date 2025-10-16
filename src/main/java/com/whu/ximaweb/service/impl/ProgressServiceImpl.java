package com.whu.ximaweb.service.impl;

import com.obs.services.model.ObsObject;
import com.whu.ximaweb.service.PhotoProcessor; // 导入照片处理器
import com.whu.ximaweb.model.PhotoData;
import com.whu.ximaweb.mapper.ActualProgressMapper;
import com.whu.ximaweb.mapper.BuildingFloorInfoMapper;
import com.whu.ximaweb.service.ObsService;
import com.whu.ximaweb.service.ProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ProgressService 接口的实现类。
 */
@Service
public class ProgressServiceImpl implements ProgressService {

    @Autowired
    private ActualProgressMapper actualProgressMapper;

    @Autowired
    private BuildingFloorInfoMapper buildingFloorInfoMapper;

    @Autowired
    private ObsService obsService;

    // 创建照片处理器的一个实例，以便后续使用
    private final PhotoProcessor photoProcessor = new PhotoProcessor();

    /**
     * refreshActualProgress 方法的最终实现。
     * 它将完成从连接云端、下载数据流到解析元数据的完整过程。
     */
    @Override
    public void refreshActualProgress() {
        System.out.println("正在执行刷新实际进度的业务逻辑...");

        // 1. 从华为云 OBS 获取文件列表
        List<ObsObject> objects = obsService.listObjects();

        if (objects.isEmpty()) {
            System.out.println("操作完成，但在云存储桶中没有找到任何文件。");
            return;
        }

        System.out.println("成功连接到华为云 OBS！找到了 " + objects.size() + " 个文件，开始逐个处理...");

        // 创建一个列表，用来收集所有成功解析出的 PhotoData 对象
        List<PhotoData> allPhotoData = new ArrayList<>();

        // 2. 遍历云端文件列表
        for (ObsObject objectSummary : objects) {
            String objectKey = objectSummary.getObjectKey(); // 获取文件名

            // 过滤掉非图片文件（例如文件夹或其他文件）
            if (!objectKey.toLowerCase().endsWith(".jpg") && !objectKey.toLowerCase().endsWith(".jpeg")) {
                continue;
            }

            // 3. 获取单个文件的输入流
            try (InputStream inputStream = obsService.getObjectInputStream(objectKey)) {
                if (inputStream != null) {
                    // 4. 将输入流交给 PhotoProcessor 处理
                    Optional<PhotoData> photoDataOptional = photoProcessor.process(inputStream, objectKey);

                    // 5. 如果处理成功，就将结果添加到列表中
                    photoDataOptional.ifPresent(allPhotoData::add);
                }
            } catch (Exception e) {
                System.err.println("处理文件 " + objectKey + " 的流时发生错误: " + e.getMessage());
            }
        }

        System.out.println("\n--- 所有云端文件处理完毕 ---");
        System.out.println("总共成功解析了 " + allPhotoData.size() + " 张照片的数据：");

        // 6. 打印出所有成功解析的数据
        for (PhotoData data : allPhotoData) {
            System.out.println(data.toString());
        }

        // TODO: 下一步将在这里对 allPhotoData 列表进行 H1/H2 分类、按天分组和计算。
    }
}