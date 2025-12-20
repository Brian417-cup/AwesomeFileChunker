package com.util;

import java.io.*;
import java.util.function.Consumer;

public class FileSplitUtil {

    public static final long DEFAULT_CHUNK_SIZE = 100 * 1024 * 1024;


    /**
     * 分割文件，并通过 progressConsumer 回调进度（0.0 ~ 1.0）
     * @return 分割生成的文件数量
     */
    public static int splitFile(File sourceFile, File outputDir, long chunkSize, Consumer<Double> progressConsumer)
            throws IOException {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IllegalArgumentException("输入必须是一个有效文件");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("分片大小必须大于 0");
        }

        // 确定输出目录
        if (outputDir == null) {
            outputDir = sourceFile.getParentFile(); // 默认：原文件目录
        } else if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) { // 自动创建多级目录
                throw new IOException("无法创建输出目录: " + outputDir.getAbsolutePath());
            }
        } else if (!outputDir.isDirectory()) {
            throw new IllegalArgumentException("输出路径不是有效文件夹: " + outputDir.getAbsolutePath());
        }

        String fileName = sourceFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
        String extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex);

        long totalSize = sourceFile.length();
        if (totalSize == 0) {
            return 0;
        }

        long chunkCount = (totalSize + chunkSize - 1) / chunkSize; // 向上取整
        if (chunkCount <= 1) {
            throw new IllegalArgumentException(
                    String.format("文件大小（%d 字节）小于或等于分片大小（%d 字节），无需分割。", totalSize, chunkSize)
            );
        }

        long bytesReadTotal = 0;
        int partCounter = 1;

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(sourceFile))) {
            byte[] buffer = new byte[8192];

            while (bytesReadTotal < totalSize) {
                String partName = String.format("%s_%02d%s", baseName, partCounter, extension);
                File partFile = new File(outputDir, partName);

                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(partFile))) {
                    long bytesWrittenToPart = 0;
                    int bytesRead;

                    while (bytesWrittenToPart < chunkSize &&
                            (bytesRead = bis.read(buffer, 0, (int) Math.min(buffer.length, chunkSize - bytesWrittenToPart))) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        bytesWrittenToPart += bytesRead;
                        bytesReadTotal += bytesRead;

                        // ✅ 修正：使用正确的参数名 progressConsumer
                        if (progressConsumer != null) {
                            progressConsumer.accept((double) bytesReadTotal / totalSize);
                        }
                    }
                }
                partCounter++;
            }
        }

        return partCounter - 1;
    }
}
