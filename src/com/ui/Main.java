package com.ui;

import com.util.FontUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.InputStream;


public class Main extends Application {
    // 定义静态常量
    public static final String APP_NAME = "文件分割/合并工具";
    public static final String MAIN_VIEW_PATH = "/com/view/FileSplitAndCombine.fxml";
    public static final String FONT_PATH = "/com/resource/font/wqy-microhei.ttf";

    public static void main(String[] args) {
        // write your code here
        launch(args);
    }

    @Override
    public void init() throws Exception {
        super.init();
//        Font.loadFont((InputStream)Main.class.getResourceAsStream(FONT_PATH), (double)8.0);
        // 加载中文字体支持
        try {
            FontUtil.loadChineseFont(FONT_PATH);
        } catch (Exception e) {
            System.err.println("警告：中文字体加载失败，界面可能显示异常");
            e.printStackTrace();
            // 继续启动（但中文可能显示为方框）
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 加载时引用常量
        Parent root = FXMLLoader.load(getClass().getResource(MAIN_VIEW_PATH));
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle(APP_NAME);
        primaryStage.show();
    }
}
