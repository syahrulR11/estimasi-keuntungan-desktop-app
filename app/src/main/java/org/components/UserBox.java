package org.components;

import java.io.IOException;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

public class UserBox extends HBox {

    @FXML private Label nameLabel;
    @FXML private ImageView avatar;

    public UserBox() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/components/UserBox.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Gagal load UserBox.fxml", e);
        }
    }

    public void setUserData(String name, Image img) {
        nameLabel.setText(name);
        avatar.setImage(img);
        if (img.isError()) {
            System.out.println("Gagal load gambar: " + img.getException());
        }
    }
}
