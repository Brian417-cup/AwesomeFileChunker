package com.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileMergeUtil {

    // 匹配: base_01.ext, base_001.ext 也支持（但你当前用 %02d，所以至少两位）
    private static final Pattern CHUNK_PATTERN = Pattern.compile("^(.+?)_(\\d+)\\.([^.]+)$");

    public static File mergeFiles(File folder, Consumer<Double> progressCallback) throws IOException {
        if (folder == null || !folder.isDirectory()) {
            throw new IllegalArgumentException("必须指定一个有效文件夹");
        }

        // 1. 扫描所有文件，找出合法分片
        List<File> allFiles = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("文件夹为空");
        }

        for (File f : files) {
            if (f.isFile()) {
                allFiles.add(f);
            }
        }

        // 2. 分组：Map<基础名+扩展名, List<分片信息>>
        Map<String, List<ChunkInfo>> groups = new HashMap<>();
        for (File file : allFiles) {
            Matcher m = CHUNK_PATTERN.matcher(file.getName());
            if (m.matches()) {
                String base = m.group(1);
                String numberStr = m.group(2);
                String ext = "." + m.group(3); // 保留点号

                // 跳过非数字编号（虽然正则已保证，但 double-check）
                try {
                    int num = Integer.parseInt(numberStr);
                    if (num <= 0) continue; // 编号必须 > 0
                    String groupName = base + ext;
                    groups.computeIfAbsent(groupName, k -> new ArrayList<>())
                            .add(new ChunkInfo(file, num));
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        }

        // 3. 过滤出“有效组”：编号连续、从1开始、至少2个
        List<String> validGroups = new ArrayList<>();
        for (Map.Entry<String, List<ChunkInfo>> entry : groups.entrySet()) {
            List<ChunkInfo> chunks = entry.getValue();
            if (isValidChunkGroup(chunks)) {
                validGroups.add(entry.getKey());
            }
        }

        // 4. 必须有且仅有一组有效分片
        if (validGroups.isEmpty()) {
            throw new IllegalArgumentException("未找到有效的分片文件组（需至少2个连续编号的分片，如 xxx_01.ext, xxx_02.ext）");
        }
        if (validGroups.size() > 1) {
            throw new IllegalArgumentException("检测到多组分片文件，请确保文件夹中只包含一组待合并的分片：\n" +
                    String.join("\n", validGroups));
        }

        String targetGroup = validGroups.get(0);
        List<ChunkInfo> chunks = groups.get(targetGroup);
        chunks.sort(Comparator.comparingInt(c -> c.number)); // 按编号排序

        // 5. 检查是否已存在合并后的完整文件
        String outputFileName = targetGroup; // e.g., "report.pdf"
        File outputFile = new File(folder, outputFileName);
        if (outputFile.exists()) {
            // 可选择抛出异常，或允许覆盖（这里选择提示用户）
            throw new IllegalArgumentException("目标文件已存在，无法合并（请先删除）：\n" + outputFile.getName());
            // ⚠️ 如果你希望自动覆盖，可跳过此检查，但风险高
        }

        // 6. 执行合并
        long totalSize = chunks.stream().mapToLong(c -> c.file.length()).sum();
        long written = 0;

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            byte[] buffer = new byte[8192];
            for (ChunkInfo chunk : chunks) {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(chunk.file))) {
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        written += bytesRead;
                        if (progressCallback != null) {
                            progressCallback.accept((double) written / totalSize);
                        }
                    }
                }
            }
        }

        return outputFile;
    }

    public static File mergeFilesInOrder(List<File> files, File outputFile, Consumer<Double> progressCallback)
            throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("文件列表为空");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("输出文件不能为空");
        }

        // 计算总大小
        long totalSize = 0;
        for (File f : files) {
            if (!f.exists() || !f.isFile()) {
                throw new IllegalArgumentException("无效文件: " + f.getAbsolutePath());
            }
            totalSize += f.length();
        }

        long written = 0;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            byte[] buffer = new byte[8192];
            for (File part : files) {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(part))) {
                    int bytesRead;
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        written += bytesRead;
                        if (progressCallback != null) {
                            progressCallback.accept((double) written / totalSize);
                        }
                    }
                }
            }
        }

        return outputFile;
    }

    // 判断一组分片是否有效：从1开始、连续、至少2个
    private static boolean isValidChunkGroup(List<ChunkInfo> chunks) {
        if (chunks.size() < 2) return false;

        chunks.sort(Comparator.comparingInt(c -> c.number));
        int expected = 1;
        for (ChunkInfo chunk : chunks) {
            if (chunk.number != expected) {
                return false; // 编号不连续或不从1开始
            }
            expected++;
        }
        return true;
    }

    // 辅助类
    private static class ChunkInfo {
        final File file;
        final int number;

        ChunkInfo(File file, int number) {
            this.file = file;
            this.number = number;
        }
    }

    /**
     * 根据清单文件（每行一个分片文件名）合并文件
     * @param chunkDir 分片所在目录
     * @param manifestFile 清单文件（文本，UTF-8，每行一个文件名，支持 # 注释）
     * @param outputFile 合并后的输出文件
     * @param progressCallback 进度回调
     * @return 合并后的文件
     */
    public static File mergeWithManifest(
            File chunkDir,
            File manifestFile,
            File outputFile,
            Consumer<Double> progressCallback) throws IOException {

        if (chunkDir == null || !chunkDir.isDirectory()) {
            throw new IllegalArgumentException("分片目录无效: " + (chunkDir != null ? chunkDir.getPath() : "null"));
        }
        if (manifestFile == null || !manifestFile.isFile()) {
            throw new IllegalArgumentException("清单文件无效: " + (manifestFile != null ? manifestFile.getPath() : "null"));
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("输出文件不能为空");
        }

        List<File> orderedFiles = new ArrayList<>();
        List<String> lines = Files.readAllLines(manifestFile.toPath(), StandardCharsets.UTF_8);
        int lineNumber = 0;

        for (String line : lines) {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue; // 跳过空行和注释
            }

            File chunkFile = new File(chunkDir, line);
            if (!chunkFile.exists() || !chunkFile.isFile()) {
                throw new IOException(String.format("清单第 %d 行文件不存在: %s", lineNumber, line));
            }
            orderedFiles.add(chunkFile);
        }

        if (orderedFiles.isEmpty()) {
            throw new IllegalArgumentException("清单中未包含任何有效分片");
        }

        return mergeFilesInOrder(orderedFiles, outputFile, progressCallback);
    }

    /**
     * 自动生成默认顺序清单（按 _01, _02, ... 数字下标排序）
     * @param chunkDir 分片所在目录
     * @param manifestFile 输出的清单文件
     */
    public static void generateDefaultManifest(File chunkDir, File manifestFile) throws IOException {
        if (chunkDir == null || !chunkDir.isDirectory()) {
            throw new IllegalArgumentException("目录无效: " + chunkDir);
        }
        if (manifestFile == null) {
            throw new IllegalArgumentException("清单文件不能为空");
        }

        File[] files = chunkDir.listFiles((dir, name) -> {
            // 匹配你生成的分片格式：xxx_01.ext
            return name.matches("^.+?_\\d+\\.[^.]+$");
        });

        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("目录中未找到分片文件");
        }

        // 按数字下标排序（关键！）
        Arrays.sort(files, (a, b) -> {
            int numA = extractChunkNumber(a.getName());
            int numB = extractChunkNumber(b.getName());
            return Integer.compare(numA, numB);
        });

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(manifestFile), StandardCharsets.UTF_8))) {
            for (File f : files) {
                writer.write(f.getName());
                writer.newLine();
            }
        }
    }

    // 辅助：从文件名如 "data_03.log" 提取 3
    private static int extractChunkNumber(String filename) {
        int lastUnderscore = filename.lastIndexOf('_');
        int lastDot = filename.lastIndexOf('.');
        if (lastUnderscore == -1 || lastDot == -1 || lastUnderscore >= lastDot) {
            return Integer.MAX_VALUE; // 无法解析的放最后
        }
        try {
            return Integer.parseInt(filename.substring(lastUnderscore + 1, lastDot));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * 从分片清单推断原始文件名
     * 例如: ["data_01.bin", "data_02.bin"] → "data.bin"
     */
    public static String inferOriginalFileName(List<File> chunkFiles) {
        if (chunkFiles == null || chunkFiles.isEmpty()) {
            throw new IllegalArgumentException("分片列表为空");
        }

        // 取第一个分片文件名分析
        String first = chunkFiles.get(0).getName();
        int lastUnderscore = first.lastIndexOf('_');
        int lastDot = first.lastIndexOf('.');

        // 必须满足: xxx_数字.ext
        if (lastUnderscore > 0 && lastDot > lastUnderscore + 1) {
            String afterUnderscore = first.substring(lastUnderscore + 1, lastDot);
            if (afterUnderscore.matches("\\d+")) { // 确保是纯数字
                String base = first.substring(0, lastUnderscore);
                String ext = first.substring(lastDot);
                return base + ext;
            }
        }

        // 降级：若无法解析，用第一个文件名去掉 "_数字"
        String fallback = first.replaceAll("_[0-9]+(\\.[^.]+)?$", "$1");
        return fallback.isEmpty() ? "merged_output" : fallback;
    }

    /**
     * 按清单合并，自动推断输出文件名
     * @param chunkDir 分片所在目录
     * @param manifestFile 清单文件
     * @param outputDir 输出目录（非文件！）
     * @param progressCallback 进度回调
     * @return 合并后的文件
     */
    public static File mergeWithManifestToDir(
            File chunkDir,
            File manifestFile,
            File outputDir,
            Consumer<Double> progressCallback) throws IOException {

        if (outputDir == null) {
            throw new IllegalArgumentException("输出目录不能为空");
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("无法创建输出目录: " + outputDir);
        }
        if (!outputDir.isDirectory()) {
            throw new IllegalArgumentException("输出路径不是有效目录: " + outputDir);
        }

        // 读取清单文件，构建 File 列表
        List<File> orderedFiles = new ArrayList<>();
        List<String> lines = Files.readAllLines(manifestFile.toPath(), StandardCharsets.UTF_8);
        int lineNumber = 0;
        for (String line : lines) {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            File f = new File(chunkDir, line);
            if (!f.exists() || !f.isFile()) {
                throw new IOException("清单第 " + lineNumber + " 行文件不存在: " + line);
            }
            orderedFiles.add(f);
        }

        // 推断输出文件名
        String outputFileName = inferOriginalFileName(orderedFiles);
        File outputFile = new File(outputDir, outputFileName);

        // 执行合并
        return mergeFilesInOrder(orderedFiles, outputFile, progressCallback);
    }
}