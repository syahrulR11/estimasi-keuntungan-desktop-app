package org.services;

import javafx.scene.Node;
import org.models.User;

import java.util.Arrays;

public final class Acl {
    private Acl() {}

    public static boolean has(String code) {
        User u = Session.user();
        return u != null && u.hasAccess(code);
    }

    public static boolean hasAny(String... codes) {
        if (codes == null || codes.length == 0) return true; // tanpa rule = selalu boleh
        return Arrays.stream(codes).anyMatch(Acl::has);
    }

    public static void show(Node n, boolean visible) {
        if (n == null) return;
        n.setVisible(visible);
        n.setManaged(visible);
    }

    /** Tampilkan node hanya jika user punya salah satu akses */
    public static void requireAny(Node n, String... codes) {
        show(n, hasAny(codes));
    }
}
