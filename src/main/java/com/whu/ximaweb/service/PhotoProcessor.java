package com.whu.ximaweb.service;

import com.whu.ximaweb.model.PhotoData;
import org.apache.commons.imaging.Imaging;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 照片处理器（最终精简版）
 * 彻底移除了EXIF逻辑，只从XMP中获取所有数据。
 */
@Component
public class PhotoProcessor {

    // 定义一个日期格式化模板，用于解析XMP中的日期字符串 "2025-10-14T17:03:12+08:00"
    private static final DateTimeFormatter XMP_DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    public Optional<PhotoData> process(InputStream inputStream, String fileName) {
        try {
            // 将输入流一次性读入字节数组，避免流被重复读取的问题
            byte[] imageBytes = inputStream.readAllBytes();

            // 直接从字节数组中获取 XMP 的 XML 字符串
            final String xmpXml = Imaging.getXmpXml(imageBytes);

            if (xmpXml == null || xmpXml.isEmpty()) {
                System.err.println("跳过文件: " + fileName + " (未找到XMP元数据)");
                return Optional.empty();
            }

            // --- 核心逻辑：所有数据均从 XMP 中解析 ---

            // 1. 解析日期 (使用新的辅助方法)
            LocalDate captureDate = parseXmpAttributeAsDate(xmpXml, "xmp:CreateDate");

            // 2. 解析其他XMP数据
            double distance = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetDistance");
            double latitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetLat");
            double longitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetLon");
            double absAltitude = parseXmpAttributeAsDouble(xmpXml, "drone-dji:LRFTargetAbsAlt");

            // 3. 数据有效性检查
            if (captureDate == null || distance == -1) {
                 System.err.println("跳过文件: " + fileName + " (缺少关键元数据: 日期=" + captureDate + ", 距离=" + distance + ")");
                return Optional.empty();
            }

            // 4. 创建并返回 PhotoData 对象
            PhotoData.MeasurementType type = PhotoData.MeasurementType.UNKNOWN;
            PhotoData data = new PhotoData(fileName, captureDate, type, distance, absAltitude, latitude, longitude);
            return Optional.of(data);

        } catch (Exception e) {
            System.err.println("处理文件 " + fileName + " 时发生严重错误: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 新增的辅助方法，用于从XMP中解析日期字符串。
     * @param xmpXml 完整的XMP XML字符串
     * @param attributeName 要查找的日期属性名 (例如 "xmp:CreateDate")
     * @return 解析出的 LocalDate 对象，如果失败则返回 null
     */
    private LocalDate parseXmpAttributeAsDate(String xmpXml, String attributeName) {
        String dateString = findXmpAttributeValue(xmpXml, attributeName);
        if (dateString != null) {
            try {
                // 使用 ISO_OFFSET_DATE_TIME 格式化器来解析带时区信息的完整日期时间字符串
                return LocalDate.parse(dateString, XMP_DATE_FORMATTER);
            } catch (Exception e) {
                System.err.println("解析日期字符串失败: " + dateString);
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

    /**
     * 抽离出的通用辅助方法，用于从XMP中查找任何属性的值。
     * @param xmpXml 完整的XMP XML字符串
     * @param attributeName 要查找的属性名
     * @return 属性的值（字符串），如果找不到则返回 null
     */
    private String findXmpAttributeValue(String xmpXml, String attributeName) {
        if (xmpXml == null) {
            return null;
        }
        Pattern pattern = Pattern.compile(attributeName + "=\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(xmpXml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}