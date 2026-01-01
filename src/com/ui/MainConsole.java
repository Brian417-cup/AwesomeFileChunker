package com.ui;

import com.resource.Profile;
import com.util.FileMergeUtil;
import com.util.PathUtil;
import com.util.FileSplitUtil;
import com.util.LogSplitUtil;

import java.io.File;
import java.util.Scanner;

public class MainConsole {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== 文件分割/合并工具 ===");
        System.out.println("1. 分割文件");
        System.out.println("2. 分割日志");
        System.out.println("3. 合并文件");
        System.out.println("4. 合并文件（指定顺序）");
        System.out.println("5. 工具简介");
        System.out.print("请选择（按1~5）: ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline

        switch (choice) {
            case 1:
                splitFile(scanner);
                break;
            case 2:
                splitLog(scanner);
                break;
            case 3:
                mergeFiles(scanner);
                break;
            case 4:
                mergeInCustomOrder(scanner);
                break;
            case 5:
                showAbout();
                break;
            default:
                System.out.println("无效选项");
                break;
        }
    }

    private static long parseChunkSize(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("分片大小不能为空");
        }

        // 去除空格并转为大写
        String s = input.trim().toUpperCase();

        // 正则匹配：数字 + 可选单位（K/KB, M/MB, G/GB）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "^(\\d+)([KMGT]B?)?$"
        );
        java.util.regex.Matcher matcher = pattern.matcher(s);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("格式错误，支持如: 100, 100M, 2GB");
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        // 默认单位：MB（与你原逻辑一致）
        if (unit == null || unit.isEmpty()) {
            return value * 1024 * 1024; // MB → bytes
        }

        switch (unit.charAt(0)) {
            case 'K':
                return value * 1024;
            case 'M':
                return value * 1024 * 1024;
            case 'G':
                return value * 1024 * 1024 * 1024;
            case 'T':
                return value * 1024L * 1024 * 1024 * 1024;
            default:
                throw new IllegalArgumentException("未知单位: " + unit);
        }
    }

    private static void splitFile(Scanner scanner) {
        // 1. 输入源文件路径
        System.out.print("请输入要分割的文件路径: ");
        String sourcePath = scanner.nextLine().trim();
        sourcePath = PathUtil.sanitizePath(sourcePath);

        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            System.err.println("❌ 错误：文件不存在或不是有效文件: " + sourcePath);
            return;
        }

        // 2. 输入分片大小（单位：MB）
        System.out.print("请输入分片大小（支持 100M, 2G, 512KB，默认单位 MB）: ");
        String sizeInput = scanner.nextLine().trim();

        long chunkSizeBytes;
        try {
            if (sizeInput.isEmpty()) {
                chunkSizeBytes = FileSplitUtil.DEFAULT_CHUNK_SIZE; // 100 MB
            } else {
                chunkSizeBytes = parseChunkSize(sizeInput); // ✅ 使用新解析函数
            }
            if (chunkSizeBytes <= 0) {
                System.err.println("❌ 分片大小必须大于 0");
                return;
            }
        } catch (Exception e) {
            System.err.println("❌ " + e.getMessage());
            return;
        }

        // 3. 输出目录（可选）
        System.out.print("请输入输出目录（留空则使用源文件所在目录）: ");
        String outputDirPath = scanner.nextLine().trim();
        outputDirPath = PathUtil.sanitizePath(outputDirPath);
        File outputDir = outputDirPath.isEmpty() ? null : new File(outputDirPath);

        // 4. 执行分割 + 显示进度
        try {
            System.out.println("\n正在分割文件，请稍候...");
            int partCount = FileSplitUtil.splitFile(
                    sourceFile,
                    outputDir,
                    chunkSizeBytes,
                    progress -> {
                        // 简单进度条：覆盖同一行
                        int percent = (int) (progress * 100);
                        System.out.print("\r进度: " + percent + "%");
                        if (percent == 100) {
                            System.out.println(); // 换行
                        }
                    }
            );

            System.out.println("✅ 分割完成！共生成 " + partCount + " 个分片文件。");
            if (outputDir != null) {
                System.out.println("输出目录: " + outputDir.getAbsolutePath());
            } else {
                System.out.println("输出目录: " + sourceFile.getParentFile().getAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("\n❌ 分割失败: " + e.getMessage());

        }

    }

    private static void splitLog(Scanner scanner) {
        System.out.print("请输入要分割的日志文件路径（必须是文本文件）: ");
        String logPath = scanner.nextLine().trim();
        logPath = PathUtil.sanitizePath(logPath);
        File logFile = new File(logPath);

        if (!logFile.exists() || !logFile.isFile()) {
            System.err.println("❌ 错误：日志文件不存在或无效: " + logPath);
            return;
        }

        System.out.print("请输入每个分片的行数（例如 10000）: ");
        String linesInput = scanner.nextLine().trim();
        int linesPerChunk;
        try {
            linesPerChunk = Integer.parseInt(linesInput);
            if (linesPerChunk <= 0) {
                System.err.println("❌ 行数必须大于 0");
                return;
            }
        } catch (NumberFormatException e) {
            System.err.println("❌ 请输入有效的整数");
            return;
        }

        System.out.print("请输入输出目录（必须为空或不存在，程序将自动创建）: ");
        String outDirPath = scanner.nextLine().trim();
        outDirPath = PathUtil.sanitizePath(outDirPath);
        if (outDirPath.isEmpty()) {
            System.err.println("❌ 输出目录不能为空");
            return;
        }
        File outputDir = new File(outDirPath);

        try {
            System.out.println("\n正在分割日志文件，请稍候...");
            int partCount = LogSplitUtil.splitLogFile(
                    logFile,
                    outputDir,
                    linesPerChunk,
                    progress -> {
                        int percent = (int) (progress * 100);
                        System.out.print("\r进度: " + percent + "%");
                        if (percent == 100) {
                            System.out.println();
                        }
                    }
            );

            if (partCount == 0) {
                System.out.println("⚠️ 文件为空，未生成分片。");
            } else {
                System.out.println("✅ 日志分割完成！共生成 " + partCount + " 个分片文件。");
                System.out.println("输出目录: " + outputDir.getAbsolutePath());
            }

        } catch (Exception e) {
            System.err.println("\n❌ 日志分割失败: " + e.getMessage());
            // e.printStackTrace(); // 开发阶段可开启
        }
    }

    private static void mergeFiles(Scanner scanner) {
        System.out.print("请输入包含分片文件的文件夹路径: ");
        String folderPath = scanner.nextLine().trim();
        folderPath = PathUtil.sanitizePath(folderPath);
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("❌ 错误：路径不是有效文件夹: " + folderPath);
            return;
        }

        try {
            System.out.println("\n正在分析分片文件并合并，请稍候...");
            File mergedFile = FileMergeUtil.mergeFiles(folder, progress -> {
                int percent = (int) (progress * 100);
                System.out.print("\r进度: " + percent + "%");
                if (percent == 100) {
                    System.out.println();
                }
            });

            System.out.println("✅ 合并成功！完整文件: " + mergedFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("\n❌ 合并失败: " + e.getMessage());
            // e.printStackTrace(); // 开发时可开启
        }
    }

    private static void mergeInCustomOrder(Scanner scanner) {
        System.out.print("请输入包含分片文件的目录: ");
        File chunkDir = new File(PathUtil.sanitizePath(scanner.nextLine().trim()));
        if (!chunkDir.isDirectory()) {
            System.err.println("❌ 无效分片目录");
            return;
        }

        // 生成默认清单
        File manifestFile = new File(chunkDir, "!merge_order.txt");
        try {
            FileMergeUtil.generateDefaultManifest(chunkDir, manifestFile);
            System.out.println("\n✅ 已生成默认清单: " + manifestFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("❌ 生成清单失败: " + e.getMessage());
            return;
        }

        System.out.println("\n📝 请用文本编辑器调整清单中的文件顺序");
        System.out.println("（每行一个文件名，无需路径，保存后按回车继续）");
        System.out.print("→ 调整完毕后按回车: ");
        scanner.nextLine(); // 等待用户编辑完成

        // ✅ 只需输入输出目录（不再是完整文件路径）
        System.out.print("请输入合并后输出目录（留空则使用分片目录）: ");
        String outDirInput = PathUtil.sanitizePath(scanner.nextLine().trim());
        File outputDir = outDirInput.isEmpty() ? chunkDir : new File(outDirInput);

        try {
            System.out.println("\n正在合并...");
            File result = FileMergeUtil.mergeWithManifestToDir(
                    chunkDir,
                    manifestFile,
                    outputDir,
                    progress -> {
                        int p = (int) (progress * 100);
                        System.out.print("\r进度: " + p + "%");
                        if (p == 100) System.out.println();
                    }
            );
            System.out.println("✅ 合并成功: " + result.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("❌ 合并失败: " + e.getMessage());
        }
    }

    private static void showAbout() {
        String aboutText = String.format(
                "%s %s\n" +
                        "======================\n\n" +

                        "【功能说明】\n" +
                        "• 文件分割：支持按字节（KB/MB/GB）或按行数分割大文件\n" +
                        "• 文件合并：自动识别分片文件（xxx_01.ext 格式）并合并\n" +
                        "• 自定义输出：分片可输出到任意目录（日志分割允许非空目录）\n" +
                        "• 顺序控制：通过清单文件（!merge_order.txt）自定义合并顺序\n\n" +

                        "【使用提示】\n" +
                        "• 分割二进制文件时，输出目录可非空（但不覆盖同名分片）\n" +
                        "• 合并时程序会自动生成默认清单，用户可编辑后合并\n" +
                        "• 日志分割输出目录允许包含其他文件\n" +
                        "• 清单文件每行一个分片文件名（无需路径）\n\n" +

                        "【作者】\n" +
                        "%s\n\n" +

                        "【版权】\n" +
                        "© %s 个人工具。保留所有权利。\n",
                Profile.APP_NAME, Profile.VERSION, Profile.AUTHOR, Profile.OUT_YEAR
        );
        System.out.println(aboutText);
    }
}
