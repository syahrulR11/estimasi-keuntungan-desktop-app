package org.controllers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import org.models.Role;
import org.models.RoleResponse;
import org.util.Http;
import org.util.Util;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.components.AlertBox;

import java.util.*;
import java.util.stream.Collectors;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.LocalDate;

public class DataRoleController {

    // ===== FXML =====
    @FXML private TextField tfId, tfName, tfCari;
    @FXML private TextArea  taAccesses;
    @FXML private TableView<Role> table;
    @FXML private TableColumn<Role,String> colId, colName, colCount, colList;
    @FXML private javafx.scene.layout.FlowPane accessContainer;
    @FXML private CheckBox cbSelectAll;
    @FXML private Button btnCetak;

    // ===== State =====
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Ganti sesuai endpoint backend-mu bila berbeda
    private static final String BASE = "api/role";

    // ====== daftar akses (konstan) ======
    private static final List<String> ALL_ACCESSES = List.of(
        "api.auth.login","api.profile.get","api.profile.update",
        "api.user.get","api.user.create","api.user.edit","api.user.update","api.user.delete",
        "api.role.get","api.role.create","api.role.edit","api.role.update","api.role.delete",
        "api.keuangan.get","api.keuangan.create",
        "api.model.get","api.model.koef","api.model.predict","api.model.preprocessing","api.model.preprocessing.image"
    );

    // map access → CheckBox
    private final Map<String, CheckBox> accessBoxes = new LinkedHashMap<>();

    private void buildAccessCheckboxes() {
        accessContainer.getChildren().clear();
        accessBoxes.clear();
        for (String a : ALL_ACCESSES) {
            CheckBox cb = new CheckBox(a);
            cb.getStyleClass().add("form-input"); // biar tinggi seragam, opsional
            accessBoxes.put(a, cb);
            accessContainer.getChildren().add(cb);
        }
    }

    private List<String> getSelectedAccesses() {
        return accessBoxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey)
                .toList();
    }

    private void setSelectedAccesses(List<String> selected) {
        accessBoxes.values().forEach(cb -> cb.setSelected(false));
        if (selected == null) return;
        for (String a : selected) {
            CheckBox cb = accessBoxes.get(a);
            if (cb != null) cb.setSelected(true);
        }
        cbSelectAll.setSelected(selected.size() == ALL_ACCESSES.size());
    }

    @FXML
    public void initialize() {
        // Tabel
        colId.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null && c.getValue().getId() != null ? String.valueOf(c.getValue().getId()) : "-"));
        colName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? Optional.ofNullable(c.getValue().getName()).orElse("-") : "-"));
        colCount.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? String.valueOf(c.getValue().getAccessCount()) : "0"));
        colList.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? c.getValue().getAccessesJoined() : "-"));

        // pilih baris → isi form
        table.getSelectionModel().selectedItemProperty().addListener((o, old, r) -> fillForm(r));

        // cari (filter lokal)
        tfCari.textProperty().addListener((obs, o, q) -> filterLocal(q));

        // load data awal
        buildAccessCheckboxes();
        cbSelectAll.selectedProperty().addListener((o, old, v) -> {
            accessBoxes.values().forEach(cb -> cb.setSelected(v));
        });

        onRefresh();
    }

    // ====== Actions ======
    @FXML private void onRefresh() { loadTable(); }

    @FXML private void onReset() {
        clearForm();
        table.getSelectionModel().clearSelection();
    }

    @FXML private void onSimpan() {
        String name = tfName.getText() == null ? "" : tfName.getText().trim();
        if (name.isEmpty()) { alert("Nama role wajib diisi."); return; }

        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("accesses", getSelectedAccesses());

        doRequest("POST", BASE, payload, "Berhasil menyimpan role.");
    }

    @FXML private void onUbah() {
        Integer id = parseId();
        if (id == null) { alert("Pilih data di tabel untuk diubah."); return; }
        String name = tfName.getText() == null ? "" : tfName.getText().trim();
        if (name.isEmpty()) { alert("Nama role wajib diisi."); return; }

        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("accesses", getSelectedAccesses());

        doRequest("PUT", BASE + "/" + id, payload, "Berhasil mengubah role.");
    }

    @FXML private void onHapus() {
        Integer id = parseId();
        if (id == null) { alert("Pilih data di tabel untuk dihapus."); return; }

        if (!confirm("Hapus role terpilih?")) return;

        doRequest("DELETE", BASE + "/" + id, null, "Berhasil menghapus role.");
    }

    @FXML
    private void onCetak() {
        List<org.models.Role> snapshot = new ArrayList<>(table.getItems());
        btnCetak.setDisable(true);

        javafx.concurrent.Task<Path> task = new javafx.concurrent.Task<>() {
            @Override protected Path call() throws Exception { return exportTableToPdf(snapshot); }
        };
        task.setOnSucceeded(e -> {
            btnCetak.setDisable(false);
            Path out = task.getValue();
            info("Berhasil export PDF: " + out.toAbsolutePath().toString());
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { java.awt.Desktop.getDesktop().open(out.toFile()); } catch (Exception ignore) {}
            });
        });
        task.setOnFailed(ev -> {
            btnCetak.setDisable(false);
            alert(errMsg(task.getException(), "Gagal export PDF."));
        });

        new Thread(task, "export-pdf").start();
    }

    // ====== Core ======
    private void loadTable() {
        Task<List<Role>> task = new Task<>() {
            @Override protected List<Role> call() throws Exception {
                Http http = new Http();
                String resp = http.GET(BASE, null);
                RoleResponse r = mapper.readValue(resp, RoleResponse.class);
                if (!r.isStatus()) throw new IllegalStateException(r.getMessage());
                return r.getData() == null ? Collections.emptyList() : r.getData();
            }
        };
        task.setOnSucceeded(e -> {
            table.getItems().setAll(task.getValue());
            filterLocal(tfCari.getText());
        });
        task.setOnFailed(e -> alert(errMsg(task.getException(), "Gagal memuat data.")));
        new Thread(task, "get-roles").start();
    }

    private void doRequest(String method, String path, Map<String,Object> body, String successMsg) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                Http http = new Http();
                String json = body == null ? "{}" : mapper.writeValueAsString(body);
                String resp;
                switch (method) {
                    case "POST" -> resp = http.POST(path, json);
                    case "PUT"  -> resp = http.PUT(path, json);     // kalau backend butuh PUT asli, ubah Http util
                    case "DELETE" -> resp = http.DELETE(path);    // atau sediakan Http.DELETE
                    default -> throw new IllegalArgumentException("Method tidak didukung: " + method);
                }
                RoleResponse r = mapper.readValue(resp, RoleResponse.class);
                if (!r.isStatus()) throw new IllegalStateException(r.getMessage());
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            info(successMsg);
            clearForm();
            loadTable();
        });
        task.setOnFailed(e -> alert(errMsg(task.getException(), "Operasi gagal.")));
        new Thread(task, "role-" + method.toLowerCase()).start();
    }

    // ====== Helpers ======
    private void fillForm(Role r) {
        if (r == null) return;
        tfId.setText(r.getId() == null ? "" : String.valueOf(r.getId()));
        tfName.setText(r.getName() == null ? "" : r.getName());
        setSelectedAccesses(r.getRoleAccesses());
    }

    private void clearForm() {
        tfId.clear(); tfName.clear();
        setSelectedAccesses(java.util.Collections.emptyList());
    }

    private Integer parseId() {
        try {
            String s = tfId.getText();
            return (s == null || s.isBlank()) ? null : Integer.parseInt(s.trim());
        } catch (Exception e) { return null; }
    }

    private void filterLocal(String q) {
        if (q == null || q.isBlank()) { table.setItems(table.getItems()); return; }
        final String needle = q.toLowerCase(Locale.ROOT);
        List<Role> src = new ArrayList<>(table.getItems());
        List<Role> filtered = src.stream().filter(r ->
                (r.getName() != null && r.getName().toLowerCase(Locale.ROOT).contains(needle)) ||
                (r.getRoleAccesses() != null && r.getAccessesJoined().toLowerCase(Locale.ROOT).contains(needle))
        ).collect(Collectors.toList());
        table.getItems().setAll(filtered);
    }

    private void info(String msg) {
        Util.alert(msg, AlertBox.Type.INFO, 3, null);
        // Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        // a.setHeaderText(null); a.setTitle("Info");
        // a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        // a.showAndWait();
    }

    private void alert(String msg) {
        Util.alert(msg, AlertBox.Type.ERROR, 3, null);
        // Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        // a.setHeaderText(null); a.setTitle("Info");
        // a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        // a.showAndWait();
    }

    private boolean confirm(String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null); a.setTitle("Konfirmasi");
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    private String errMsg(Throwable ex, String fallback) {
        return ex != null && ex.getMessage() != null && !ex.getMessage().isBlank() ? ex.getMessage() : fallback;
    }

    private static final float MARGIN            = 36f;
    private static final float PDF_FONT_SIZE     = 10f;
    private static final float PDF_TITLE_SIZE    = 14f;
    private static final float PDF_ROW_LEADING   = 12f;  // jarak antar baris teks wrap
    private static final float PDF_ROW_GAP       = 8;   // jarak antar baris tabel (ruang untuk separator)

    private int headersListIndex(String header) {
        return switch (header) {
            case "ID" -> 0;
            case "Nama" -> 1;
            case "#Akses" -> 2;
            default -> 3;
        };
    }

    private Path exportTableToPdf(List<org.models.Role> rows) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font     = PDType1Font.HELVETICA;
            PDType1Font fontBold = PDType1Font.HELVETICA_BOLD;

            float pageW = PDRectangle.A4.getWidth();
            float pageH = PDRectangle.A4.getHeight();

            // --- Logo (sekali saja) ---
            PDImageXObject logo = null;
            try (InputStream is = getClass().getResourceAsStream("/org/assets/icon.png")) {
                if (is != null) logo = PDImageXObject.createFromByteArray(doc, is.readAllBytes(), "logo");
            }

            // Lebar kolom: ID, Nama, #Akses, Daftar Akses
            final float[] widths = {
                50f, 180f, 70f, (pageW - 2 * MARGIN) - (50f + 180f + 70f)
            };
            final String[] headers = {"ID", "Nama", "#Akses", "Daftar Akses"};

            // Helper width string (handle IOException)
            java.util.function.Function<String, Float> strW = (s) -> {
                String t = (s == null) ? "" : s;
                try { return font.getStringWidth(t) / 1000f * PDF_FONT_SIZE; }
                catch (Exception e) { return (float)(t.length() * 0.55 * PDF_FONT_SIZE); }
            };

            // Wrap paragraf polos (tanpa prefix)
            java.util.function.BiFunction<String, Float, List<String>> wrap = (text, width) -> {
                List<String> lines = new ArrayList<>();
                if (text == null || text.isBlank()) { lines.add(""); return lines; }
                String[] words = text.split("\\s+");
                StringBuilder cur = new StringBuilder();
                float maxW = width - 4f; // padding kiri-kanan tipis
                for (String w : words) {
                    String probe = (cur.length() == 0) ? w : (cur + " " + w);
                    if (strW.apply(probe) > maxW) {
                        if (cur.length() > 0) lines.add(cur.toString());
                        cur = new StringBuilder(w);
                    } else cur = new StringBuilder(probe);
                }
                if (cur.length() > 0) lines.add(cur.toString());
                return lines;
            };

            // Wrap list bernomor → hasilkan pasangan (prefix, teks)
            record LineSeg(String prefix, String text) {}
            java.util.function.BiFunction<List<String>, Float, List<LineSeg>> numberWrap = (items, width) -> {
                List<LineSeg> out = new ArrayList<>();
                if (items == null) return out;
                for (int i = 0; i < items.size(); i++) {
                    String body = items.get(i) == null ? "" : items.get(i);
                    String prefix = (i + 1) + ". ";
                    float prefixW = strW.apply(prefix);
                    float bodyW = Math.max(40f, width - prefixW);
                    List<String> chunks = wrap.apply(body, bodyW);
                    for (int li = 0; li < chunks.size(); li++) {
                        out.add(new LineSeg(li == 0 ? prefix : "", chunks.get(li)));
                    }
                }
                if (out.isEmpty()) out.add(new LineSeg("", ""));
                return out;
            };

            // --- Start halaman pertama ---
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float x = MARGIN, y = pageH - MARGIN;

            // ===== Kop Surat =====
            float logoW = 36f, logoH = 36f;
            if (logo != null) cs.drawImage(logo, x, y - logoH, logoW, logoH);
            float tx = (logo != null) ? x + logoW + 10f : x;

            cs.beginText(); cs.setFont(fontBold, PDF_TITLE_SIZE); cs.newLineAtOffset(tx, y - 14f);
            cs.showText("PT QUDWAH BERKAH SEJAHTERA"); cs.endText();

            cs.beginText(); cs.setFont(font, 11f); cs.newLineAtOffset(tx, y - 30f);
            cs.showText("Bidang Usaha: Al-Qudwah"); cs.endText();

            cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(tx, y - 44f);
            cs.showText("Alamat: Jl. Maulana Hasanuddin, Kp. Cempa, Ds. Cilangkap, Kec. Kalanganyar - Kab. Lebak"); cs.endText();

            y -= 56f;
            cs.moveTo(x, y); cs.lineTo(pageW - MARGIN, y); cs.setLineWidth(0.6f); cs.stroke();
            y -= 16f;

            // ===== Judul & header kolom =====
            cs.beginText(); cs.setFont(fontBold, 12f); cs.newLineAtOffset(x, y);
            cs.showText("Daftar Role"); cs.endText(); y -= 18f;

            cs.setFont(fontBold, 10f);
            float cx = x;
            for (String h : headers) {
                cs.beginText(); cs.newLineAtOffset(cx + 2, y); cs.showText(h); cs.endText();
                cx += widths[headersListIndex(h)];
            }
            y -= 22f;
            cs.setFont(font, PDF_FONT_SIZE);

            // ===== Data baris =====
            for (int i = 0; i < rows.size(); i++) {
                org.models.Role r = rows.get(i);

                String vId   = (r.getId() == null) ? "-" : String.valueOf(r.getId());
                String vName = (r.getName() == null) ? "-" : r.getName();
                String vCnt  = String.valueOf(r.getAccessCount());
                List<String> accList = (r.getRoleAccesses() == null) ? List.of() : r.getRoleAccesses();

                var accessLines = numberWrap.apply(accList, widths[3]);
                float contentH  = Math.max(18f, accessLines.size() * PDF_ROW_LEADING);
                float requiredH = contentH + PDF_ROW_GAP;

                // Page break bila tidak cukup ruang untuk baris + gap
                if (y - requiredH < MARGIN) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageH - MARGIN;

                    // Kop surat ulang
                    if (logo != null) cs.drawImage(logo, x, y - logoH, logoW, logoH);
                    tx = (logo != null) ? x + logoW + 10f : x;
                    cs.beginText(); cs.setFont(fontBold, PDF_TITLE_SIZE); cs.newLineAtOffset(tx, y - 14f);
                    cs.showText("PT QUDWAH BERKAH SEJAHTERA"); cs.endText();
                    cs.beginText(); cs.setFont(font, 11f); cs.newLineAtOffset(tx, y - 30f);
                    cs.showText("Bidang Usaha: Al-Qudwah"); cs.endText();
                    cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(tx, y - 44f);
                    cs.showText("Alamat: Jl. Maulana Hasanuddin, Kp. Cempa, Ds. Cilangkap, Kec. Kalanganyar - Kab. Lebak"); cs.endText();

                    y -= 56f; cs.moveTo(x, y); cs.lineTo(pageW - MARGIN, y); cs.setLineWidth(0.6f); cs.stroke();
                    y -= 16f;

                    // Header kolom ulang
                    cs.beginText(); cs.setFont(fontBold, 12f); cs.newLineAtOffset(x, y);
                    cs.showText("Daftar Role"); cs.endText(); y -= 18f;

                    cs.setFont(fontBold, 10f);
                    cx = x;
                    for (String h : headers) {
                        cs.beginText(); cs.newLineAtOffset(cx + 2, y); cs.showText(h); cs.endText();
                        cx += widths[headersListIndex(h)];
                    }
                    y -= 22f;
                    cs.setFont(font, PDF_FONT_SIZE);
                }

                // Kolom 1–3 (single line)
                float cy = y;
                cx = x;
                String[] vals = { vId, vName, vCnt };
                for (int k = 0; k < 3; k++) {
                    cs.beginText(); cs.newLineAtOffset(cx + 2, cy); cs.showText(vals[k]); cs.endText();
                    cx += widths[k];
                }

                // Kolom 4: daftar akses bernomor, wrap
                float ax = x + widths[0] + widths[1] + widths[2];
                float ay = y;
                for (var seg : accessLines) {
                    float offsetX = 2f;
                    if (!seg.prefix().isEmpty()) {
                        cs.beginText(); cs.newLineAtOffset(ax + 2, ay); cs.showText(seg.prefix()); cs.endText();
                        offsetX += strW.apply(seg.prefix());
                    }
                    cs.beginText(); cs.newLineAtOffset(ax + offsetX, ay); cs.showText(seg.text()); cs.endText();
                    ay -= PDF_ROW_LEADING;
                }

                // Separator tipis di tengah gap (tidak memotong teks)
                if (i != rows.size() - 1) {
                    float sepY = y - contentH + (PDF_ROW_GAP * 0.8f);
                    cs.setLineWidth(0.2f); cs.moveTo(x, sepY); cs.lineTo(pageW - MARGIN, sepY); cs.stroke();
                }

                y -= requiredH; // maju ke baris berikutnya (konten + gap)
            }

            cs.close();

            Path dir = Paths.get(System.getProperty("user.home"), "Documents");
            Files.createDirectories(dir);
            Path out = dir.resolve("roles-" + LocalDate.now() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }
}