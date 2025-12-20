package com.controller.handler;

import com.util.FileSplitUtil;
import com.util.LogSplitUtil;
import com.controller.SplitMode;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;

public class FileSplitHandler {

    private final TextField splitPathTextArea;
    private final TextField chunkSizeField;
    private final javafx.scene.control.ComboBox<String> chunkUnitComboBox;
    private final javafx.scene.control.ComboBox<SplitMode> splitTypeComboBox;
    private final TextField splitOutputDirField;
    private final Button splitSubmitBtn;
    private final Button splitInputBtn;
    private final Button splitOutputDirBtn;
    private final ProgressBar splitProgressBar;

    public FileSplitHandler(
            TextField splitPathTextArea,
            TextField chunkSizeField,
            javafx.scene.control.ComboBox<String> chunkUnitComboBox,
            javafx.scene.control.ComboBox<SplitMode> splitTypeComboBox,
            TextField splitOutputDirField,
            Button splitSubmitBtn,
            Button splitInputBtn,
            Button splitOutputDirBtn,
            ProgressBar splitProgressBar) {
        this.splitPathTextArea = splitPathTextArea;
        this.chunkSizeField = chunkSizeField;
        this.chunkUnitComboBox = chunkUnitComboBox;
        this.splitTypeComboBox = splitTypeComboBox;
        this.splitOutputDirField = splitOutputDirField;
        this.splitSubmitBtn = splitSubmitBtn;
        this.splitInputBtn = splitInputBtn;
        this.splitOutputDirBtn = splitOutputDirBtn;
        this.splitProgressBar = splitProgressBar;

        initProgressBar();
    }

    public void initProgressBar() {
        splitProgressBar.setVisible(false);
    }

    public void bindActions() {
        splitInputBtn.setOnAction(e -> chooseFile());
        splitOutputDirBtn.setOnAction(e -> chooseOutputDir());
        splitSubmitBtn.setOnAction(e -> startSplitProcess());
    }

    private void chooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择要分割的文件");
        File selectedFile = fileChooser.showOpenDialog(splitInputBtn.getScene().getWindow());
        if (selectedFile != null) {
            splitPathTextArea.setText(selectedFile.getAbsolutePath());
        }
    }

    private void chooseOutputDir() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("选择分片输出文件夹");
        String currentPath = splitPathTextArea.getText().trim();
        if (!currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                dirChooser.setInitialDirectory(currentFile.getParentFile());
            }
        }
        File selectedDir = dirChooser.showDialog(splitOutputDirBtn.getScene().getWindow());
        if (selectedDir != null && selectedDir.isDirectory()) {
            splitOutputDirField.setText(selectedDir.getAbsolutePath());
        }
    }

    private void startSplitProcess() {
        SplitMode mode = splitTypeComboBox.getValue();
        if (mode == null) {
            showAlert("错误", "请选择分割模式");
            return;
        }

        if (mode == SplitMode.LOG) {
            startLogSplit();
        } else if (mode == SplitMode.FILE) {
            startFileSplit();
        } else {
            showAlert("错误", "不支持当前类型");
        }
    }


    // 注意：所有 showAlert 要改为 static 或传入 owner

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    // ===== 以下是简化版，你可粘贴原逻辑 =====
    private void startFileSplit() {
        String path = splitPathTextArea.getText();
        if (path == null || path.trim().isEmpty()) {
            showAlert("错误", "请选择一个有效的文件路径！");
            return;
        }

        File file = new File(path.trim());
        if (!file.exists()) {
            showAlert("错误", "文件不存在！");
            return;
        }
        if (!file.isFile()) {
            showAlert("错误", "请选择一个文件，而不是文件夹！");
            return;
        }

        // 分析分片大小
        String sizeText = chunkSizeField.getText().trim();
        if (sizeText.isEmpty()) {
            showAlert("错误", "请输入分片大小！");
            return;
        }

        long numericSize;
        try {
            numericSize = Long.parseLong(sizeText);
        } catch (NumberFormatException e) {
            showAlert("错误", "分片大小必须是有效数字！");
            return;
        }

        if (numericSize <= 0) {
            showAlert("错误", "分片大小必须大于 0！");
            return;
        }

        String unit = chunkUnitComboBox.getValue();
        if (unit == null) {
            showAlert("错误", "请选择分片单位（KB/MB/GB）！");
            return;
        }

        long chunkSizeBytes;
        switch (unit) {
            case "KB":
                chunkSizeBytes = numericSize * 1024L;
                break;
            case "MB":
                chunkSizeBytes = numericSize * 1024L * 1024L;
                break;
            case "GB":
                chunkSizeBytes = numericSize * 1024L * 1024L * 1024L;
                break;
            default:
                showAlert("错误", "不支持的单位：" + unit);
                return;
        }

        // 防止内存溢出或不合理值（例如 > 1TB）
        if (chunkSizeBytes <= 0 || chunkSizeBytes > 1L * 1024 * 1024 * 1024 * 1024) { // >1TB 视为非法
            showAlert("错误", "分片大小超出合理范围（建议 1KB ~ 1TB）！");
            return;
        }

        // 解析输出目录
        String outputDirText = splitOutputDirField.getText().trim();
        if (outputDirText.isEmpty()) {
            showAlert("要求", "请指定一个空的输出文件夹（目标路径不能为空）");
            return;
        }
        File outputDir = new File(outputDirText);

        // 可选：提前校验（非必须，但可更快反馈）
        if (outputDir.exists() && outputDir.isDirectory()) {
            File[] files = outputDir.listFiles();
            if (files != null && files.length > 0) {
                showAlert("错误", "输出文件夹非空！\n请清空或选择新文件夹。");
                return;
            }
        }

        // 禁用按钮防止重复提交
        splitSubmitBtn.setDisable(true);
        splitInputBtn.setDisable(true);

//        显示进度条
        splitProgressBar.setVisible(true);

        // 创建返回 Integer 的 Task
        Task<Integer> splitTask = new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                return FileSplitUtil.splitFile(file, outputDir, chunkSizeBytes, progress -> {
                    updateProgress(progress, 1.0);
                });
            }
        };

        // 绑定进度条（不变）
        splitProgressBar.progressProperty().bind(splitTask.progressProperty());

        splitTask.setOnSucceeded(e -> {
            cleanupProgressBar();
            Integer partCount = splitTask.getValue();
            if (partCount == null || partCount <= 0) {
                showAlert("警告", "未生成任何分片文件。");
                return;
            }

            // 获取原文件信息
            String originalPath = splitPathTextArea.getText().trim();
            File originalFile = new File(originalPath);
            String fileName = originalFile.getName();
            int dotIndex = fileName.lastIndexOf('.');
            String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
            String extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex); // 包含点号，如 ".pdf"

            // 生成示例文件名（最多显示 5 个，避免太长）
            int showCount = Math.min(partCount, 5);
            StringBuilder exampleNames = new StringBuilder();
            for (int i = 1; i <= showCount; i++) {
                if (i > 1) exampleNames.append(", ");
                exampleNames.append(String.format("%s_%02d%s", baseName, i, extension));
            }
            if (partCount > 5) {
                exampleNames.append(", ...");
            }

            File actualOutputDir = (outputDir != null) ? outputDir : file.getParentFile();
            String firstPartName = String.format("%s_%02d%s", baseName, 1, extension);
            String examplePath = new File(actualOutputDir, firstPartName).getAbsolutePath();

            String message = String.format(
                    "文件分片完成！\n共生成 %d 个分片文件。\n输出目录：\n%s\n示例文件：\n%s",
                    partCount, actualOutputDir.getAbsolutePath(), examplePath
            );

            showAlert("成功", message);
        });

        splitTask.setOnFailed(e -> {
            cleanupProgressBar();
            Throwable ex = splitTask.getException();
            String msg = ex != null ? ex.getMessage() : "未知错误";
            if (msg == null || msg.trim().isEmpty()) msg = "文件分片失败";
            showAlert("失败", "分割过程中发生错误：\n" + msg);
        });

        // 启动线程
        Thread thread = new Thread(splitTask, "FileSplit-Worker");
        thread.setDaemon(true);
        thread.start();
    }


    private void startLogSplit() {
        // 1. 校验源文件
        String path = splitPathTextArea.getText().trim();
        if (path.isEmpty()) {
            showAlert("错误", "请选择要分割的日志文件！");
            return;
        }
        File sourceFile = new File(path);
        if (!sourceFile.exists()) {
            showAlert("错误", "文件不存在！");
            return;
        }
        if (!sourceFile.isFile()) {
            showAlert("错误", "请选择一个日志文件，而不是文件夹！");
            return;
        }

        // 2. 校验分片行数
        String lineText = chunkSizeField.getText().trim();
        int linesPerChunk;
        try {
            linesPerChunk = Integer.parseInt(lineText);
            if (linesPerChunk <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showAlert("错误", "分片行数必须是正整数！");
            return;
        }

        // 3. 校验输出目录（强制指定且为空）
        String outputDirText = splitOutputDirField.getText().trim();
        if (outputDirText.isEmpty()) {
            showAlert("要求", "请指定一个空的输出文件夹（不能为空）");
            return;
        }
        File outputDir = new File(outputDirText);

        // 提前校验输出目录（可选，但提升体验）
        if (outputDir.exists()) {
            if (!outputDir.isDirectory()) {
                showAlert("错误", "输出路径不是一个文件夹！");
                return;
            }
            File[] files = outputDir.listFiles();
            if (files != null && files.length > 0) {
                showAlert("错误", "输出文件夹非空！\n请清空或选择新文件夹。");
                return;
            }
        }
        // 如果不存在，LogSplitUtil 会自动创建

        // 4. 禁用 UI 防止重复操作
        splitSubmitBtn.setDisable(true);
        splitInputBtn.setDisable(true);
        splitOutputDirBtn.setDisable(true);

//        显示进度条
        splitProgressBar.setVisible(true);

        // 5. 创建后台任务
        Task<Integer> logSplitTask = new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                return LogSplitUtil.splitLogFile(sourceFile, outputDir, linesPerChunk, progress -> {
                    updateProgress(progress, 1.0);
                });
            }
        };

        // 6. 绑定进度条
        splitProgressBar.progressProperty().bind(logSplitTask.progressProperty());

        // 7. 成功回调
        logSplitTask.setOnSucceeded(e -> {
            // 恢复 UI
            cleanupProgressBar();

            // 显示结果
            Integer partCount = logSplitTask.getValue();
            if (partCount == null || partCount <= 0) {
                showAlert("警告", "未生成任何分片文件。");
                return;
            }

            // 构建成功消息（与文件分割一致）
            String fileName = sourceFile.getName();
            int dotIndex = fileName.lastIndexOf('.');
            String baseName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
            String extension = (dotIndex == -1) ? "" : fileName.substring(dotIndex);

            int showCount = Math.min(partCount, 5);
            StringBuilder examples = new StringBuilder();
            for (int i = 1; i <= showCount; i++) {
                if (i > 1) examples.append(", ");
                examples.append(String.format("%s_%02d%s", baseName, i, extension));
            }
            if (partCount > 5) {
                examples.append(", ...");
            }

            String message = String.format(
                    "日志分割完成！\n共生成 %d 个分片文件。\n输出目录：\n%s\n示例文件：\n%s",
                    partCount,
                    outputDir.getAbsolutePath(),
                    examples.toString()
            );
            showAlert("成功", message);
        });

        // 8. 失败回调
        logSplitTask.setOnFailed(e -> {
            cleanupProgressBar();

            Throwable ex = logSplitTask.getException();
            String msg = (ex != null && ex.getMessage() != null) ? ex.getMessage() : "未知错误";
            showAlert("失败", "日志分割失败：\n" + msg);
        });

        // 9. 启动线程
        Thread thread = new Thread(logSplitTask, "LogSplit-Worker");
        thread.setDaemon(true);
        thread.start();
    }


    private void cleanupProgressBar() {
        splitSubmitBtn.setDisable(false);
        splitInputBtn.setDisable(false);
        splitProgressBar.progressProperty().unbind();
        splitProgressBar.setProgress(0.0); // 重置进度条
        splitProgressBar.setVisible(false);
    }
}