package com.whu.ximaweb.service;

import com.whu.ximaweb.model.PhotoData;
import org.apache.commons.imaging.Imaging;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 照片处理器
 * 负责解析照片文件的 XMP 元数据，提取激光测距、经纬度和高度信息
 */
@Component
public class PhotoProcessor {

    // XMP 时间格式通常是 ISO 8601
    private static final DateTimeFormatter XMP_DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public Optional<PhotoData> process(InputStream inputStream, String fileName) {
        try {
            byte[] imageBytes = inputStream.readAllBytes();
            final String xmpXml = Imaging.getXmpXml(imageBytes);

            if (xmpXml == null || xmpXml.isEmpty()) {
                return Optional.empty();
            }

            // 1. 解析时间
            LocalDateTime captureTime = parseXmpAttributeAsTime(xmpXml, "xmp:CreateDate");

            // 2. 解析关键数据
            // 激光测距距离 (H1)
            double distance = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetDistance");
            // 目标点经纬度
            double latitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetLat");
            double longitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetLon");
            // 目标点绝对高度 (注意：这是激光打到的点的高度)
            double targetAbsAltitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetAbsAlt");

            // ✅ 新增：解析无人机自身的绝对飞行高度 (用于 H2 智能推算)
            double droneAbsAltitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:AbsoluteAltitude");

            // 3. 容错处理：时间解析兜底
            if (captureTime == null) {
                captureTime = parseXmpAttributeAsTime(xmpXml, "photoshop:DateCreated");
            }
            if (captureTime == null) {
                 // 如果实在没有时间，暂不处理该照片
                 return Optional.empty();
            }

            // 4. 构建传输对象
            PhotoData.MeasurementType type = PhotoData.MeasurementType.UNKNOWN;

            // ✅ 修改：传入新增的 droneAbsAltitude 参数
            // 参数顺序必须与 PhotoData.java 的构造函数一致：
            // fileName, captureTime, type, distance, targetAbsAltitude, droneAbsoluteAltitude, latitude, longitude
            PhotoData data = new PhotoData(
                    fileName,
                    captureTime,
                    type,
                    distance,
                    targetAbsAltitude,
                    droneAbsAltitude, // 这里传入了新解析的数据
                    latitude,
                    longitude
            );

            return Optional.of(data);

        } catch (Exception e) {
            System.err.println("处理文件 " + fileName + " 失败: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析带时区的时间字符串
     */
    private LocalDateTime parseXmpAttributeAsTime(String xmpXml, String attributeName) {
        String dateString = findXmpAttributeValue(xmpXml, attributeName);
        if (dateString != null) {
            try {
                return OffsetDateTime.parse(dateString, XMP_DATE_FORMATTER).toLocalDateTime();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private double parseXmpAttributeAsDouble(String xmpXml, String attributeName) {
        String valueStr = findXmpAttributeValue(xmpXml, attributeName);
        if (valueStr != null) {
            try {
                return Double.parseDouble(valueStr);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    private String findXmpAttributeValue(String xmpXml, String attributeName) {
        if (xmpXml == null) return null;
        Pattern pattern = Pattern.compile(attributeName + "=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(xmpXml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}