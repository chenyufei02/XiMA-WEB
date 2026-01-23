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

@Component
public class PhotoProcessor {

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

            // 2. 解析距离和高度
            double distance = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetDistance");
            double targetAbsAltitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetAbsAlt");
            double droneAbsAltitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:AbsoluteAltitude");

            // 3. 解析两套坐标
            // Set A: 飞机自身坐标
            double droneLat = parseXmpAttributeAsDouble(xmpXml, "drone-dji:GpsLatitude");
            double droneLng = parseXmpAttributeAsDouble(xmpXml, "drone-dji:GpsLongitude");

            // Set B: 激光目标点坐标 (新增)
            double targetLat = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetLat");
            double targetLng = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetLon");

            // 兜底：如果飞机坐标没解析到，尝试用目标点填充
            if (droneLat == -1) droneLat = targetLat;
            if (droneLng == -1) droneLng = targetLng;

            // 4. 时间兜底
            if (captureTime == null) {
                captureTime = parseXmpAttributeAsTime(xmpXml, "photoshop:DateCreated");
            }
            if (captureTime == null) {
                 return Optional.empty();
            }

            PhotoData data = new PhotoData(
                    fileName,
                    captureTime,
                    PhotoData.MeasurementType.UNKNOWN,
                    distance,
                    targetAbsAltitude,
                    droneAbsAltitude,
                    droneLat,
                    droneLng,
                    targetLat,  // 传入目标点
                    targetLng
            );

            return Optional.of(data);

        } catch (Exception e) {
            System.err.println("处理文件 " + fileName + " 失败: " + e.getMessage());
            return Optional.empty();
        }
    }

    private LocalDateTime parseXmpAttributeAsTime(String xmpXml, String attributeName) {
        String dateString = findXmpAttributeValue(xmpXml, attributeName);
        if (dateString != null) {
            try {
                return OffsetDateTime.parse(dateString, XMP_DATE_FORMATTER).toLocalDateTime();
            } catch (Exception e) { return null; }
        }
        return null;
    }

    private double parseXmpAttributeAsDouble(String xmpXml, String attributeName) {
        String valueStr = findXmpAttributeValue(xmpXml, attributeName);
        if (valueStr != null) {
            try {
                return Double.parseDouble(valueStr);
            } catch (NumberFormatException e) { return -1; }
        }
        return -1;
    }

    private String findXmpAttributeValue(String xmpXml, String attributeName) {
        if (xmpXml == null) return null;
        Pattern pattern = Pattern.compile(attributeName + "=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(xmpXml);
        if (matcher.find()) return matcher.group(1);
        return null;
    }
}