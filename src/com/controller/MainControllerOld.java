package com.controller;

import com.util.FileMergeUtil;
import com.util.FileSplitUtil;
import com.util.LogSplitUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class MainControllerOld {
    // ===== 分割区域控件 =====
    @FXML
    private TextField chunkSizeField;

    @FXML
    private ComboBox<String> chunkUnitComboBox;

    @FXML
    private TextField splitPathTextArea;

    @FXML
    private ProgressBar splitProgressBar;

    @FXML
    private Button splitSubmitBtn;

    @FXML
    private Button splitInputBtn;

    @FXML
    private ComboBox<SplitMode> splitTypeComboBox;

    @FXML
    private TextField splitOutputDirField;

    @FXML
    private Button splitOutputDirBtn;

    // ===== 合并区域控件 =====
    @FXML
    private TextField mergeFolderPathField;

    @FXML
    private Button mergeSelectFolderBtn;

    @FXML
    private Button mergeSubmitBtn;

    @FXML
    private ProgressBar mergeProgressBar;

    @FXML
    private Text sizeOrLineLabel;

    @FXML
    private TableView<File> mergeFileTable;

    @FXML
    private TableColumn<File, String> fullPathColumn;   // 完整路径

    @FXML
    private TableColumn<File, String> fileNameColumn;   // 文件名

    @FXML
    private TableColumn<File, String> fileSizeColumn;   // 文件大小

    @FXML
    private Button moveUpBtn;

    @FXML
    private Button moveDownBtn;

    @FXML
    public void initialize() {

        // 初始化 ComboBox 选项（也可在 FXML 中定义，这里双重保险）
//        splitTypeComboBox.getItems().addAll("文件", "文件夹");
//        splitTypeComboBox.setValue("文件"); // 默认选中“文件”

        // 绑定“选择路径”按钮
        splitInputBtn.setOnAction(event -> chooseFile());

        // 绑定“确认提交”按钮
        splitSubmitBtn.setOnAction(event -> startSplitProcess());

        chunkUnitComboBox.getItems().addAll("KB", "MB", "GB");
        chunkUnitComboBox.setValue("MB"); // 确保一致性

        // 可限制只能输入数字（可选增强）
        chunkSizeField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*")) {
                chunkSizeField.setText(oldText);
            }
        });

        // 分割输出路径选择
        splitOutputDirBtn.setOnAction(event -> chooseSplitOutputDir());

        // 日志切割模式和文件分割模式切换
        splitTypeComboBox.getItems().addAll(SplitMode.values());
        splitTypeComboBox.setValue(SplitMode.FILE); // 默认

        // 分割类型监听
        splitTypeComboBox.setOnAction(event -> onSplitTypeChanged());

        // 合并区监听器
        mergeSelectFolderBtn.setOnAction(event -> chooseMergeFolder());
        mergeSubmitBtn.setOnAction(event -> startMergeProcess());

        // 初始化合并文件列表的 TableView
        fullPathColumn = new TableColumn<>("完整路径");
        fullPathColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getAbsolutePath())
        );
        fullPathColumn.setPrefWidth(250);

        fileNameColumn = new TableColumn<>("文件名");
        fileNameColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getName())
        );
        fileNameColumn.setPrefWidth(150);

        fileSizeColumn = new TableColumn<>("大小");
        fileSizeColumn.setCellValueFactory(data -> {
            long len = data.getValue().length();
            if (len == 0) {
                return new SimpleStringProperty("0 B");
            } else if (len < 1024) {
                return new SimpleStringProperty(len + " B");
            } else if (len < 1024 * 1024) {
                return new SimpleStringProperty(String.format("%.2f KB", len / 1024.0));
            } else {
                return new SimpleStringProperty(String.format("%.2f MB", len / (1024.0 * 1024.0)));
            }
        });

        fullPathColumn.setCellFactory(column -> {
            return new TableCell<File, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setTooltip(null);
                    } else {
                        setText(item); // JavaFX 自动省略超长文本（显示 ...）
                        setTooltip(new Tooltip(item)); // 悬停显示完整路径
                    }
                }
            };
        });

        fileSizeColumn.setPrefWidth(100);

        // 添加列到 TableView
        mergeFileTable.getColumns().addAll(fullPathColumn, fileNameColumn, fileSizeColumn);

        // ===== 完整路径列（带 Tooltip）=====
        fullPathColumn = new TableColumn<>("完整路径");
        fullPathColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAbsolutePath()));
        fullPathColumn.setPrefWidth(250);
        fullPathColumn.setCellFactory(column -> new TableCell<File, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item); // JavaFX 自动截断超长文本
                    setTooltip(new Tooltip(item)); // 👈 悬停显示完整路径
                }
            }
        });

        // ===== 文件名列（带 Tooltip）=====
        fileNameColumn = new TableColumn<>("文件名");
        fileNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        fileNameColumn.setPrefWidth(150);
        fileNameColumn.setCellFactory(column -> new TableCell<File, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    setTooltip(new Tooltip(item)); // 👈 悬停显示完整文件名
                }
            }
        });

        // ===== 文件大小列（通常不长，可不加，但加上也无妨）=====
        fileSizeColumn = new TableColumn<>("大小");
        fileSizeColumn.setCellValueFactory(data -> {
            long len = data.getValue().length();
            if (len < 1024) return new SimpleStringProperty(len + " B");
            else if (len < 1024 * 1024) return new SimpleStringProperty(String.format("%.2f KB", len / 1024.0));
            else return new SimpleStringProperty(String.format("%.2f MB", len / (1024.0 * 1024.0)));
        });
        fileSizeColumn.setPrefWidth(100);

        // 添加可悬浮标签显示文字的列到 TableView
        mergeFileTable.getColumns().clear(); // 防止重复添加
        mergeFileTable.getColumns().addAll(fullPathColumn, fileNameColumn, fileSizeColumn);


        // TableView加强版：右键选择删除选中行
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("删除选中行");

        deleteItem.setOnAction(e -> {
            File selected = mergeFileTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                mergeFileTable.getItems().remove(selected);
            }
        });

//      监听选中状态，控制菜单项是否可用
//        允许同时选中多行
        mergeFileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        mergeFileTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            deleteItem.setDisable(newVal == null);
        });

        contextMenu.getItems().add(deleteItem);
        mergeFileTable.setContextMenu(contextMenu);


        // 修改删除逻辑
        deleteItem.setOnAction(e -> {
            ObservableList<File> selectedItems =
                    FXCollections.observableArrayList(
                            mergeFileTable.getSelectionModel().getSelectedItems()
                    );
            if (!selectedItems.isEmpty()) {
                mergeFileTable.getItems().removeAll(selectedItems);
            }
        });


        moveUpBtn.setOnAction(e -> moveSelectedRow(-1));
        moveDownBtn.setOnAction(e -> moveSelectedRow(1));
    }


    private void onSplitTypeChanged() {
        SplitMode mode = splitTypeComboBox.getValue();
        if (mode == null) return;

        switch (mode) {
            case LOG:
                sizeOrLineLabel.setText("分片行数");
                chunkSizeField.setText("1000");
                chunkSizeField.setPromptText("每片行数");
                chunkUnitComboBox.getItems().setAll("行");
                chunkUnitComboBox.setValue("行");
                chunkUnitComboBox.setDisable(true);
                break;
            case FILE:
            default:
                sizeOrLineLabel.setText("分片大小");
                chunkSizeField.setText("50");
                chunkSizeField.setPromptText("分片大小数值");
                chunkUnitComboBox.getItems().setAll("KB", "MB", "GB");
                chunkUnitComboBox.setValue("MB");
                chunkUnitComboBox.setDisable(false);
                break;
        }
    }

    private void chooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择要分割的文件");
        File selectedFile = fileChooser.showOpenDialog(splitInputBtn.getScene().getWindow());

        if (selectedFile != null) {
            splitPathTextArea.setText(selectedFile.getAbsolutePath());
        }
        // 如果用户点击取消，selectedFile == null，不做处理
    }

    private void chooseSplitOutputDir() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("选择分片输出文件夹");
        // 可选：设置初始目录为原文件所在目录（如果已选择）
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
            showAlert("错误", "不支持的模式: " + mode);
        }
    }

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
            cleanup();
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
            cleanup();
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
            splitSubmitBtn.setDisable(false);
            splitInputBtn.setDisable(false);
            splitOutputDirBtn.setDisable(false);
            splitProgressBar.progressProperty().unbind();
            splitProgressBar.setProgress(0.0);

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
            splitSubmitBtn.setDisable(false);
            splitInputBtn.setDisable(false);
            splitOutputDirBtn.setDisable(false);
            splitProgressBar.progressProperty().unbind();
            splitProgressBar.setProgress(0.0);

            Throwable ex = logSplitTask.getException();
            String msg = (ex != null && ex.getMessage() != null) ? ex.getMessage() : "未知错误";
            showAlert("失败", "日志分割失败：\n" + msg);
        });

        // 9. 启动线程
        Thread thread = new Thread(logSplitTask, "LogSplit-Worker");
        thread.setDaemon(true);
        thread.start();
    }

    private void cleanup() {
        splitSubmitBtn.setDisable(false);
        splitInputBtn.setDisable(false);
        splitProgressBar.progressProperty().unbind();
        splitProgressBar.setProgress(0.0); // 重置进度条
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void chooseMergeFolder() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("选择包含分片文件的文件夹");
        File selectedDir = dirChooser.showDialog(mergeSelectFolderBtn.getScene().getWindow());
        if (selectedDir != null && selectedDir.isDirectory()) {
            mergeFolderPathField.setText(selectedDir.getAbsolutePath());
            mergeFileTable.getItems().clear();
            loadFilesIntoTable(selectedDir);
        }
    }

    private void loadFilesIntoTable(File folder) {
        File[] files = folder.listFiles(f -> f.isFile());
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName)); // 可选：保留默认排序
        ObservableList<File> fileItems = FXCollections.observableArrayList(files);
        mergeFileTable.setItems(fileItems); // ✅ 仍然传 List<File>
    }

    private String getBaseNameFromChunks(List<File> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        // 使用第一个文件推断原始文件名
        String name = files.get(0).getName();
        // 匹配格式: base_01.ext
        int underscoreIndex = name.lastIndexOf('_');
        int dotIndex = name.lastIndexOf('.');

        // 必须同时存在 _ 和 . ，且 _ 在 . 之前
        if (underscoreIndex > 0 && dotIndex > underscoreIndex) {
            String basePart = name.substring(0, underscoreIndex);
            String extPart = name.substring(dotIndex); // 包含 .
            return basePart + extPart;
        }
        return null;
    }

    private void moveSelectedRow(int direction) {
        int selectedIndex = mergeFileTable.getSelectionModel().getSelectedIndex();
        ObservableList<File> items = mergeFileTable.getItems();

        if (selectedIndex < 0 || selectedIndex >= items.size()) return;

        int newIndex = selectedIndex + direction;
        if (newIndex < 0 || newIndex >= items.size()) return;

        // ✅ 第一步：清除当前选择（关键！）
        mergeFileTable.getSelectionModel().clearSelection();

        // 第二步：移动数据
        File item = items.remove(selectedIndex);
        items.add(newIndex, item);

        // ✅ 第三步：延迟选中新位置
        Platform.runLater(() -> {
            mergeFileTable.getSelectionModel().select(newIndex);
            mergeFileTable.scrollTo(newIndex); // 确保可见
        });
    }

    private void startMergeProcess() {
        // 1. 获取用户自定义的文件列表（按 TableView 中的顺序）
        ObservableList<File> filesToMerge = mergeFileTable.getItems();
        if (filesToMerge == null || filesToMerge.isEmpty()) {
            showAlert("错误", "请先选择文件夹并加载文件列表！");
            return;
        }

        // 2. 推断原始文件名（如 report_01.pdf → report.pdf）
        String baseName = getBaseNameFromChunks(new ArrayList<>(filesToMerge));
        if (baseName == null) {
            showAlert("错误", "无法推断原始文件名，请确保文件名格式为：xxx_01.ext");
            return;
        }

        // 3. 确定输出文件路径（与分片同目录）
        File firstFile = filesToMerge.get(0);
        File outputFile = new File(firstFile.getParentFile(), baseName);

        // 4. 检查目标文件是否已存在（防覆盖）
        if (outputFile.exists()) {
            showAlert("错误", "目标文件已存在，请先删除后再合并：\n" + outputFile.getName());
            return;
        }

        // 5. 禁用 UI 防止重复操作
        mergeSubmitBtn.setDisable(true);
        mergeSelectFolderBtn.setDisable(true);

        // 6. 创建后台合并任务
        Task<File> mergeTask = new Task<File>() {
            @Override
            protected File call() throws Exception {
                return FileMergeUtil.mergeFilesInOrder(
                        new ArrayList<>(filesToMerge), // 按用户顺序
                        outputFile,
                        progress -> updateProgress(progress, 1.0)
                );
            }
        };

        // 7. 绑定进度条
        mergeProgressBar.progressProperty().bind(mergeTask.progressProperty());

        // 8. 成功回调
        mergeTask.setOnSucceeded(e -> {
            mergeSubmitBtn.setDisable(false);
            mergeSelectFolderBtn.setDisable(false);
            mergeProgressBar.progressProperty().unbind();
            mergeProgressBar.setProgress(0.0);

            File result = mergeTask.getValue();
            showAlert("成功", "文件合并完成！\n输出文件：\n" + result.getAbsolutePath());
        });

        // 9. 失败回调
        mergeTask.setOnFailed(e -> {
            mergeSubmitBtn.setDisable(false);
            mergeSelectFolderBtn.setDisable(false);
            mergeProgressBar.progressProperty().unbind();
            mergeProgressBar.setProgress(0.0);

            Throwable ex = mergeTask.getException();
            String msg = ex != null ? ex.getMessage() : "未知错误";
            if (msg == null || msg.trim().isEmpty()) {
                msg = "合并过程中发生未知错误";
            }
            showAlert("失败", "合并失败：\n" + msg);
        });

        // 10. 启动线程
        Thread thread = new Thread(mergeTask, "CustomMerge-Worker");
        thread.setDaemon(true);
        thread.start();
    }

}
