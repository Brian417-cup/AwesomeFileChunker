package com.ui;

import com.resource.Profile;
import com.util.FontUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class Main extends Application {

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
            FontUtil.loadChineseFont(Profile.FONT_PATH);
        } catch (Exception e) {
            System.err.println("警告：中文字体加载失败，界面可能显示异常");
            e.printStackTrace();
            // 继续启动（但中文可能显示为方框）
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 加载时引用常量
        Parent root = FXMLLoader.load(getClass().getResource(Profile.MAIN_VIEW_PATH));
        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle(Profile.APP_NAME);
        primaryStage.show();
    }
}
