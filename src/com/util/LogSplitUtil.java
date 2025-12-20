package com.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class LogSplitUtil {

    /**
     * 按行数分割文本文件（如日志）
     * @param sourceFile 源文件（必须是文本文件）
     * @param outputDir 输出目录（必须为空或不存在）
     * @param linesPerChunk 每个分片的行数（>0）
     * @param progressCallback 进度回调（按已读行数 / 总行数）
     */
    public static int splitLogFile(File sourceFile, File outputDir, int linesPerChunk, Consumer<Double> progressCallback)
            throws IOException {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IllegalArgumentException("输入必须是一个有效文件");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("输出目录不能为空");
        }
        if (linesPerChunk <= 0) {
            throw new IllegalArgumentException("每片行数必须大于 0");
        }

        // 校验输出目录（与 FileSplitUtil 一致）
        if (outputDir.exists()) {
            if (!outputDir.isDirectory()) {
                throw new IllegalArgumentException("输出路径存在但不是一个文件夹: " + outputDir.getAbsolutePath());
            }
            File[] files = outputDir.listFiles();
            if (files != null && files.length > 0) {
                throw new IllegalArgumentException("输出目录非空，请指定空文件夹:\n" + outputDir.getAbsolutePath());
            }
        } else {
            if (!outputDir.mkdirs()) {
                throw new IOException("无法创建输出目录: " + outputDir.getAbsolutePath());
            }
        }

        // 获取总行数（用于进度计算）
        long totalLines = countLines(sourceFile);
        if (totalLines == 0) {
            return 0;
        }

        long currentLine = 0;
        int partCounter = 1;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(sourceFile), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if ((currentLine % linesPerChunk) == 0) {
                    // 需要新开一个分片文件
                    if (currentLine > 0) {
                        // 关闭上一个分片（通过 try-with-resources 自动处理）
                    }

                    String baseName = getBaseName(sourceFile.getName());
                    String extension = getExtension(sourceFile.getName());
                    String partName = String.format("%s_%02d%s", baseName, partCounter++, extension);
                    File partFile = new File(outputDir, partName);

                    // 创建新分片的 BufferedWriter（需在循环外持有？不，我们换一种方式）
                    // 改为：每片用独立 try-with-resources
                    // → 所以重构成内部写法
                    currentLine = writeChunk(reader, partFile, linesPerChunk, line, currentLine, totalLines, progressCallback);
                } else {
                    // 不会发生，因为上面已处理
                }
            }
        }

        return partCounter - 1;
    }

    // 逐片写入
    private static long writeChunk(BufferedReader reader, File partFile, int linesPerChunk,
                                   String firstLine, long currentLine, long totalLines,
                                   Consumer<Double> progressCallback) throws IOException {
        long linesWritten = 0;
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(partFile), StandardCharsets.UTF_8))) {

            // 写入第一行（已从外层 read）
            writer.write(firstLine);
            writer.newLine();
            linesWritten++;
            currentLine++;

            // 写入后续行，直到达到 linesPerChunk 或 EOF
            String line;
            while (linesWritten < linesPerChunk && (line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
                linesWritten++;
                currentLine++;

                if (progressCallback != null) {
                    progressCallback.accept((double) currentLine / totalLines);
                }
            }
        }
        return currentLine;
    }

    // 获取总行数（用于进度）
    private static long countLines(File file) throws IOException {
        long lines = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                lines++;
            }
        }
        return lines;
    }

    // 工具方法
    private static String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
    }

    private static String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex);
    }
}