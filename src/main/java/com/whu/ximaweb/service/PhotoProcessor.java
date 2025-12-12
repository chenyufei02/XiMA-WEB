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
 * 照片处理器（升级版：精确时间解析）
 */
@Component
public class PhotoProcessor {

    // XMP 时间格式通常是 ISO 8601，带时区，例如 "2023-10-14T12:00:00+08:00"
    private static final DateTimeFormatter XMP_DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public Optional<PhotoData> process(InputStream inputStream, String fileName) {
        try {
            byte[] imageBytes = inputStream.readAllBytes();
            final String xmpXml = Imaging.getXmpXml(imageBytes);

            if (xmpXml == null || xmpXml.isEmpty()) {
                return Optional.empty();
            }

            // 1. ✅ 修改：解析为 LocalDateTime (精确到秒)
            LocalDateTime captureTime = parseXmpAttributeAsTime(xmpXml, "xmp:CreateDate");

            // 2. 解析其他数据
            double distance = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetDistance");
            double latitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetLat");
            double longitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetLon");
            double absAltitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetAbsAlt");

            // 3. 校验
            if (captureTime == null) {
                // 如果没解析出时间，尝试用另一个常用字段 DateCreated
                captureTime = parseXmpAttributeAsTime(xmpXml, "photoshop:DateCreated");
            }

            if (captureTime == null) {
                 // 如果还是没有时间，这通常是不正常的，但在抢救模式下可以暂时返回空让上层处理
                 return Optional.empty();
            }

            // 4. 返回
            PhotoData.MeasurementType type = PhotoData.MeasurementType.UNKNOWN;
            PhotoData data = new PhotoData(fileName, captureTime, type, distance, absAltitude, latitude, longitude);
            return Optional.of(data);

        } catch (Exception e) {
            System.err.println("处理文件 " + fileName + " 失败: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * ✅ 新增：解析带时区的时间字符串，并转为本地时间
     */
    private LocalDateTime parseXmpAttributeAsTime(String xmpXml, String attributeName) {
        String dateString = findXmpAttributeValue(xmpXml, attributeName);
        if (dateString != null) {
            try {
                // 先解析为 OffsetDateTime (处理+08:00)，再转为 LocalDateTime
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