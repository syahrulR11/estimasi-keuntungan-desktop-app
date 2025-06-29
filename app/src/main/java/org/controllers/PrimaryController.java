package org.controllers;

import org.components.UserBox;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;

public class PrimaryController {

    @FXML private VBox container;

    public void initialize() {
        UserBox user1 = new UserBox();
        user1.setUserData("Alice", new Image("https://placehold.co/50.png"));

        UserBox user2 = new UserBox();
        user2.setUserData("Bob", new Image("https://placehold.co/50.png"));

        container.getChildren().addAll(user1, user2);
    }
}
