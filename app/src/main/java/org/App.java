package org;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class App extends Application {

    private static Stage primaryStage;
    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        Parent root = loadFXML("pages/Login");
        scene = new Scene(root, 1200, 800);
        addStyles(scene, "assets/css/login.css");   // pastikan file ini ada
        stage.setScene(scene);
        stage.setTitle("Dashboard Estimasi Keuntungan Retail");
        stage.show();
    }

    public static void switchToMain() throws IOException {
        Parent root = loadFXML("layouts/Main");
        scene = new Scene(root, 1200, 800);
        addStyles(scene, "assets/css/main.css");
        primaryStage.setScene(scene);
        primaryStage.setTitle("Dashboard Estimasi Keuntungan Retail");
        primaryStage.show();
    }

    public static Stage getPrimaryStage() { return primaryStage; }

    public static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        var url = App.class.getResource(fxml + ".fxml");
        FXMLLoader loader = new FXMLLoader(url);
        return loader.load();
    }

    private static void addStyles(Scene s, String path) {
        var url = App.class.getResource(path);
        if (url != null) s.getStylesheets().add(url.toExternalForm());
    }

    public static void main(String[] args) {
        launch();
    }
}
