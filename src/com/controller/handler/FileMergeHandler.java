package com.controller.handler;

import com.util.FileMergeUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FileMergeHandler {

    private final TextField mergeFolderPathField;
    private final Button mergeSelectFolderBtn;
    private final Button mergeSubmitBtn;
    private final ProgressBar mergeProgressBar;
    private final TableView<File> mergeFileTable;
    private final Button moveUpBtn;
    private final Button moveDownBtn;

    public FileMergeHandler(
            TextField mergeFolderPathField,
            Button mergeSelectFolderBtn,
            Button mergeSubmitBtn,
            ProgressBar mergeProgressBar,
            TableView<File> mergeFileTable,
            Button moveUpBtn,
            Button moveDownBtn) {
        this.mergeFolderPathField = mergeFolderPathField;
        this.mergeSelectFolderBtn = mergeSelectFolderBtn;
        this.mergeSubmitBtn = mergeSubmitBtn;
        this.mergeProgressBar = mergeProgressBar;
        this.mergeFileTable = mergeFileTable;
        this.moveUpBtn = moveUpBtn;
        this.moveDownBtn = moveDownBtn;

        initTableView();
        initContextMenu();
        initProgressBar();
    }

    private void initTableView() {
        TableColumn<File, String> fullPathCol = new TableColumn<>("完整路径");
        fullPathCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getAbsolutePath()));
        fullPathCol.setPrefWidth(250);
        fullPathCol.setCellFactory(col -> new TableCell<File, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty ? null : new Tooltip(item));
            }
        });

        TableColumn<File, String> fileNameCol = new TableColumn<>("文件名");
        fileNameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        fileNameCol.setPrefWidth(150);
        fileNameCol.setCellFactory(col -> new TableCell<File, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty ? null : new Tooltip(item));
            }
        });

        TableColumn<File, String> fileSizeCol = new TableColumn<>("大小");
        fileSizeCol.setCellValueFactory(d -> {
            long len = d.getValue().length();
            if (len < 1024) return new SimpleStringProperty(len + " B");
            else if (len < 1024 * 1024) return new SimpleStringProperty(String.format("%.2f KB", len / 1024.0));
            else return new SimpleStringProperty(String.format("%.2f MB", len / (1024.0 * 1024.0)));
        });
        fileSizeCol.setPrefWidth(100);

        mergeFileTable.getColumns().setAll(fullPathCol, fileNameCol, fileSizeCol);
        mergeFileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void initContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("删除选中行");
        deleteItem.setOnAction(e -> {
            ObservableList<File> selected = FXCollections.observableArrayList(
                    mergeFileTable.getSelectionModel().getSelectedItems()
            );
            if (!selected.isEmpty()) {
                mergeFileTable.getItems().removeAll(selected);
            }
        });
        deleteItem.disableProperty().bind(
                mergeFileTable.getSelectionModel().selectedItemProperty().isNull()
        );
        contextMenu.getItems().add(deleteItem);
        mergeFileTable.setContextMenu(contextMenu);
    }

    private void initProgressBar() {
        mergeProgressBar.setVisible(false);
    }

    public void bindActions() {
        mergeSelectFolderBtn.setOnAction(e -> chooseMergeFolder());
        mergeSubmitBtn.setOnAction(e -> startMergeProcess());
        moveUpBtn.setOnAction(e -> moveSelectedRow(-1));
        moveDownBtn.setOnAction(e -> moveSelectedRow(1));
    }

    private void chooseMergeFolder() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("选择包含分片文件的文件夹");
        File dir = dirChooser.showDialog(mergeSelectFolderBtn.getScene().getWindow());
        if (dir != null && dir.isDirectory()) {
            mergeFolderPathField.setText(dir.getAbsolutePath());
            loadFilesIntoTable(dir);
        }
    }

    private void loadFilesIntoTable(File folder) {
        File[] files = folder.listFiles(f -> f.isFile());
        if (files == null) files = new File[0];
        Arrays.sort(files, Comparator.comparing(File::getName));
        mergeFileTable.setItems(FXCollections.observableArrayList(files));
    }

    private void moveSelectedRow(int direction) {
        int selectedIndex = mergeFileTable.getSelectionModel().getSelectedIndex();
        ObservableList<File> items = mergeFileTable.getItems();
        if (selectedIndex < 0 || selectedIndex >= items.size()) return;

        int newIndex = selectedIndex + direction;
        if (newIndex < 0 || newIndex >= items.size()) return;

        mergeFileTable.getSelectionModel().clearSelection();
        File item = items.remove(selectedIndex);
        items.add(newIndex, item);

        Platform.runLater(() -> {
            mergeFileTable.getSelectionModel().select(newIndex);
            mergeFileTable.scrollTo(newIndex);
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
//        同时开启可视化进度条
        mergeProgressBar.setVisible(true);

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
            mergeProgressBar.setVisible(false);

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

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

}