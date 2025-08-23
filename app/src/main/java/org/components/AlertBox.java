package org.components;

import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import javafx.scene.image.Image;
import java.io.IOException;

public class AlertBox extends HBox {

    @FXML private Label messageLabel;
    @FXML private ImageView icon;

    public enum Type {
        INFO, SUCCESS, WARNING, ERROR
    }

    public AlertBox() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/components/AlertBox.fxml"));
        loader.setController(this);
        try {
            HBox content = loader.load();
            this.getChildren().setAll(content.getChildren());
            this.setAlignment(content.getAlignment());
            this.setSpacing(content.getSpacing());
            this.setStyle(content.getStyle());
            this.setMaxWidth(content.getMaxWidth());
            this.setMaxHeight(content.getMaxHeight());
            this.setPrefWidth(content.getPrefWidth());
        } catch (IOException e) {
            throw new RuntimeException("Gagal load AlertBox.fxml", e);
        }
    }

    public void show(String message, Type type, int durationSeconds, Runnable onRemove) {
        messageLabel.setText(message);
        String iconPath = switch (type) {
            case INFO -> "/org/assets/info.png";
            case SUCCESS -> "/org/assets/success.png";
            case WARNING -> "/org/assets/warning.png";
            case ERROR -> "/org/assets/error.png";
        };
        icon.setImage(new Image(getClass().getResource(iconPath).toExternalForm()));

        PauseTransition pause = new PauseTransition(Duration.seconds(durationSeconds));
        pause.setOnFinished(e -> {
            if (this.getParent() instanceof Pane parent) {
                parent.getChildren().remove(this);
                if (onRemove != null) onRemove.run();
            }
        });
        pause.play();
    }
}
