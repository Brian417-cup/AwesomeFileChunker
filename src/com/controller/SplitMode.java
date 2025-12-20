package com.controller;

public enum SplitMode {
    FILE("文件"),
    LOG("日志");

    private final String displayName;

    SplitMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    // 用于从显示名反查 enum（可选，但推荐）
    public static SplitMode fromDisplayName(String name) {
        for (SplitMode mode : values()) {
            if (mode.displayName.equals(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("未知的分割模式: " + name);
    }

    @Override
    public String toString() {
        return displayName;
    }
}