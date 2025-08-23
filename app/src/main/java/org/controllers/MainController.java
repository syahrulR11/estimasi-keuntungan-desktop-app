package org.controllers;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import org.App;
import org.components.AlertBox;
import org.services.Acl;
import org.services.Session;
import org.util.Util;

public class MainController {

    @FXML private StackPane contentContainer;
    @FXML private VBox alertContainer;

    @FXML private Button btnBeranda;
    @FXML private Button btnDataKeuangan;
    @FXML private Button btnDataRole;
    @FXML private Button btnDataUser;
    @FXML private Button btnLaporanEstimasi;
    @FXML private Button btnTentang;
    @FXML private Button btnKeluar;

    @FXML
    public void initialize() {
        Util.setAlertContainer(alertContainer);
        loadView("Dashboard");
        setActive(btnBeranda, btnDataKeuangan, btnDataRole, btnLaporanEstimasi, btnTentang);
        Util.alert("Selamat Datang "+Session.user().getName()+"!", AlertBox.Type.SUCCESS, 3, null);
        btnBeranda.setOnAction(e -> {
            setActive(btnBeranda, btnDataKeuangan, btnDataRole, btnLaporanEstimasi, btnTentang, btnDataUser);
            loadView("Dashboard");
        });
        btnDataKeuangan.setOnAction(e -> {
            setActive(btnDataKeuangan, btnBeranda, btnDataRole, btnLaporanEstimasi, btnTentang, btnDataUser);
            loadView("DataKeuangan");
        });
        btnDataRole.setOnAction(e -> {
            setActive(btnDataRole, btnBeranda, btnDataKeuangan, btnLaporanEstimasi, btnTentang, btnDataUser);
            loadView("DataRole");
        });
        btnDataUser.setOnAction(e -> {
            setActive(btnDataUser, btnBeranda, btnDataKeuangan, btnLaporanEstimasi, btnTentang, btnDataRole);
            loadView("DataUser");
        });
        btnLaporanEstimasi.setOnAction(e -> {
            setActive(btnLaporanEstimasi, btnBeranda, btnDataKeuangan, btnDataRole, btnTentang, btnDataUser);
            loadView("LaporanEstimasi");
        });
        btnTentang.setOnAction(e -> {
            setActive(btnTentang, btnBeranda, btnDataKeuangan, btnDataRole, btnLaporanEstimasi, btnDataUser);
            loadView("Tentang");
        });
        btnKeluar.setOnAction(e -> {
            logout();
        });
        Platform.runLater(this::applyAclSidebar);
    }

    private void applyAclSidebar() {
        // Contoh mapping:
        // - Tutup Buku (data keuangan) terlihat jika punya salah satu akses get/create
        Acl.requireAny(btnDataKeuangan, "api.keuangan.get", "api.keuangan.create");

        // - Grafik Analisis jika boleh lihat model
        Acl.requireAny(btnLaporanEstimasi, "api.model.get");

        // - Menu admin
        Acl.requireAny(btnDataUser, "api.user.get");
        Acl.requireAny(btnDataRole, "api.role.get");

        // - Beranda/Tentang/Keluar selalu tampil
        Acl.show(btnBeranda, true);
        Acl.show(btnTentang, true);
        Acl.show(btnKeluar, true);
    }

    private void logout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Konfirmasi Logout");
        confirm.setHeaderText(null);
        confirm.setContentText("Anda yakin ingin keluar dari aplikasi?");
        var choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) return;
        if (btnKeluar != null) btnKeluar.setDisable(true);
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                Session.clear();
                return null;
            }
        };
        task.setOnSucceeded(ev -> {
            if (btnKeluar != null) btnKeluar.setDisable(false);
            goToLogin();
        });
        task.setOnFailed(ev -> {
            if (btnKeluar != null) btnKeluar.setDisable(false);
            goToLogin();
        });
        new Thread(task, "logout-thread").start();
    }

    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(App.class.getResource("pages/Login.fxml"));
            Scene loginScene = new Scene(loader.load(), 1200, 800);
            var css = App.class.getResource("assets/css/login.css");
            if (css != null) loginScene.getStylesheets().add(css.toExternalForm());
            Stage stage = (Stage) contentContainer.getScene().getWindow();
            stage.setScene(loginScene);
            stage.setTitle("Dashboard Estimasi Keuntungan Retail");
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            ((Stage) contentContainer.getScene().getWindow()).close();
        }
    }

    private void setActive(Button active, Button... others) {
        active.getStyleClass().add("active");
        for (Button b : others) b.getStyleClass().removeAll("active");
    }

    public void loadView(String name) {
        try {
            Parent view = FXMLLoader.load(getClass().getResource("/org/pages/" + name + ".fxml"));
            contentContainer.getChildren().setAll(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
