package com.util;

import javafx.scene.text.Font;

import java.io.InputStream;

public class FontUtil {
    public static void loadChineseFont(String fontResourcePath) {
        InputStream fontStream = FontUtil.class.getResourceAsStream(fontResourcePath);
        if (fontStream == null) {
            throw new IllegalArgumentException("字体资源未找到: " + fontResourcePath);
        }

        try (InputStream is = fontStream) {
            Font font = Font.loadFont(is, 10);
            if (font == null) {
                throw new IllegalStateException("字体加载失败（可能格式不支持）: " + fontResourcePath);
            }
            System.out.println("✅ 中文字体加载成功: " + fontResourcePath);
            System.out.println("👉 字体 Family Name 为: [" + font.getFamily() + "]");
            System.out.println("👉 字体 Full Name 为: [" + font.getName() + "]");

        } catch (Exception e) {
            throw new RuntimeException("加载中文字体失败: " + fontResourcePath, e);
        }
    }
}
