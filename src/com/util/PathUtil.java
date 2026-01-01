package com.util;

public class PathUtil {
    /**
     * 清理用户输入的文件路径：去除首尾的双引号（"）或单引号（'）
     */
    public static String sanitizePath(String input) {
        if (input == null) return null;
        input = input.trim();
        if (input.length() >= 2) {
            char first = input.charAt(0);
            char last = input.charAt(input.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return input.substring(1, input.length() - 1).trim();
            }
        }
        return input;
    }
}
