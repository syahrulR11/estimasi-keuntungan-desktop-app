package org.util;

import java.text.NumberFormat;
import java.util.Locale;
import org.components.AlertBox;
import javafx.application.Platform;
import javafx.scene.layout.VBox;

public class Util {

    private static VBox alertContainer;

    @SuppressWarnings("deprecation")
    public static String toRupiah(double nominal) {
        Locale indo = new Locale("id", "ID");
        NumberFormat formatter = NumberFormat.getCurrencyInstance(indo);
        return formatter.format(nominal);
    }

    public static void setAlertContainer(VBox container) {
        alertContainer = container;
    }

    public static void alert(String message, AlertBox.Type type, int durationSeconds, Runnable onRemove) {
        Platform.runLater(() -> {
            if (alertContainer != null) {
                AlertBox alert = new AlertBox();
                alert.show(message, type, durationSeconds, onRemove);
                alertContainer.getChildren().add(alert);
            } else {
                System.err.println("alertContainer is not set!");
            }
        });
    }
}
