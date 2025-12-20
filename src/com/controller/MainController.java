package com.controller;

import com.controller.handler.AboutMenuBarHandler;
import com.controller.handler.FileMergeHandler;
import com.controller.handler.FileSplitHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Text;

import java.io.File;

public class MainController {

    // ===== 菜单区域 =====
    @FXML
    private MenuItem aboutMenuItem;

    // ===== 分割区域 =====
    @FXML
    private TextField splitPathTextArea;
    @FXML
    private TextField chunkSizeField;
    @FXML
    private ComboBox<String> chunkUnitComboBox;
    @FXML
    private ComboBox<SplitMode> splitTypeComboBox;
    @FXML
    private TextField splitOutputDirField;
    @FXML
    private Button splitSubmitBtn;
    @FXML
    private Button splitInputBtn;
    @FXML
    private Button splitOutputDirBtn;
    @FXML
    private ProgressBar splitProgressBar;
    @FXML
    private Text sizeOrLineLabel;

    // ===== 合并区域 =====
    @FXML
    private TextField mergeFolderPathField;
    @FXML
    private Button mergeSelectFolderBtn;
    @FXML
    private Button mergeSubmitBtn;
    @FXML
    private ProgressBar mergeProgressBar;
    @FXML
    private TableView<File> mergeFileTable;
    @FXML
    private Button moveUpBtn;
    @FXML
    private Button moveDownBtn;

    private FileSplitHandler splitHandler;
    private FileMergeHandler mergeHandler;
    private AboutMenuBarHandler aboutMenuBarHandler;

    @FXML
    public void initialize() {
        // 初始化分割逻辑
        splitHandler = new FileSplitHandler(
                splitPathTextArea, chunkSizeField, chunkUnitComboBox, splitTypeComboBox,
                splitOutputDirField, splitSubmitBtn, splitInputBtn, splitOutputDirBtn, splitProgressBar
        );
        splitHandler.bindActions();

        // 初始化合并逻辑
        mergeHandler = new FileMergeHandler(
                mergeFolderPathField, mergeSelectFolderBtn, mergeSubmitBtn,
                mergeProgressBar, mergeFileTable, moveUpBtn, moveDownBtn
        );
        mergeHandler.bindActions();

        // 分割模式切换（需访问 splitHandler 内部？或保留在此）
        splitTypeComboBox.getItems().addAll(SplitMode.values());
        splitTypeComboBox.setValue(SplitMode.FILE);
        splitTypeComboBox.setOnAction(e -> onSplitTypeChanged());

        // 初始化顶部菜单“关于”逻辑
        aboutMenuBarHandler = new AboutMenuBarHandler(aboutMenuItem);
        aboutMenuBarHandler.bindActions();
    }

    private void onSplitTypeChanged() {
        SplitMode mode = splitTypeComboBox.getValue();
        if (mode == SplitMode.LOG) {
            sizeOrLineLabel.setText("分片行数");
            chunkSizeField.setText("1000");
            chunkUnitComboBox.getItems().setAll("行");
            chunkUnitComboBox.setValue("行");
            chunkUnitComboBox.setDisable(true);
        } else {
            sizeOrLineLabel.setText("分片大小");
            chunkSizeField.setText("50");
            chunkUnitComboBox.getItems().setAll("KB", "MB", "GB");
            chunkUnitComboBox.setValue("MB");
            chunkUnitComboBox.setDisable(false);
        }
    }
}