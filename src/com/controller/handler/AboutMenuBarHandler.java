package com.controller.handler;

import com.resource.Profile;
import javafx.scene.control.*;

public class AboutMenuBarHandler {
    private final MenuItem aboutMenuItem;

    public AboutMenuBarHandler(MenuItem aboutMenuItem) {
        this.aboutMenuItem = aboutMenuItem;
    }

    public void bindActions() {
        // 关于菜单
        aboutMenuItem.setOnAction(e -> showAboutDialog());
    }

    private void showAboutDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(String.format("关于 %s", Profile.APP_NAME));
        dialog.setHeaderText(null);
        dialog.setResizable(true);

        // 创建内容区域（支持滚动）
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setText(
                String.format("%s %s\n" +
                                "======================\n\n" +

                                "【功能说明】\n" +
                                "• 文件分割：支持按字节（KB/MB/GB）或按行数分割大文件\n" +
                                "• 文件合并：自动识别分片文件（xxx_01.ext 格式）并合并\n" +
                                "• 自定义输出：分片可输出到任意空文件夹\n" +
                                "• 可视化调整：合并前可调整分片顺序、删除不需要的分片\n\n" +

                                "【使用提示】\n" +
                                "• 分割时输出目录必须为空\n" +
                                "• 合并时确保分片文件名符合规范（如 log_01.txt, log_02.txt）\n" +
                                "• 右键点击合并列表可删除选中行\n" +
                                "• 拖拽或使用 ↑/↓ 按钮调整合并顺序\n\n" +

                                "【作者】\n" +
                                "%s\n\n" +

                                "【版权】\n" +
                                "© %s 个人工具。保留所有权利。",
                        Profile.APP_NAME, Profile.VERSION, Profile.AUTHOR, Profile.OUT_YEAR)
        );

        // 设置文本区域大小
        textArea.setPrefSize(500, 300);

        // 添加到对话框
        dialog.getDialogPane().setContent(textArea);

        // 添加“确定”按钮
        ButtonType okButton = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(okButton);

        // 显示对话框（模态）
        dialog.showAndWait();
    }
}
