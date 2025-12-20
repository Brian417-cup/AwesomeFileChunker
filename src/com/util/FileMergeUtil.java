package com.util;

import java.io.*;
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
}