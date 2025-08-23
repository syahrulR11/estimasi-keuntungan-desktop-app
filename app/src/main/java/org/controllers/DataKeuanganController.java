package org.controllers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.components.AlertBox;
import org.models.DataKeuangan;
import org.util.Http;
import org.util.Util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class DataKeuanganController {
    @FXML private DatePicker dpTanggal, dpStart, dpEnd;
    @FXML private TextField tfPenjualan, tfPembelian, tfStok, tfKeuntungan;
    @FXML private Button btnSimpan, btnReset, btnCetak;
    @FXML private TableView<DataKeuangan> table;
    @FXML private TableColumn<DataKeuangan, String> colTanggal, colPenjualan, colPembelian, colStok, colKeuntungan, colCreator;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ZoneId zone = ZoneId.of("Asia/Jakarta");
    private final DateTimeFormatter iso = DateTimeFormatter.ISO_DATE;

    @FXML
    public void initialize() {
        dpStart.setPromptText("Semua");
        dpEnd.setPromptText("Semua");
        btnSimpan.getStyleClass().add("btn-primary");

        // default nilai
        dpTanggal.setValue(LocalDate.now(zone));

        // Numeric only
        numericOnly(tfPenjualan);
        numericOnly(tfPembelian);
        numericOnly(tfStok);
        numericOnly(tfKeuntungan);

        // Table columns (gunakan formatting rupiah & tanggal indo)
        colTanggal.setCellValueFactory(c ->
                new SimpleStringProperty(safe(c.getValue(), DataKeuangan::getTanggalFormatIndo)));
        colPenjualan.setCellValueFactory(c ->
                new SimpleStringProperty(Util.toRupiah(safeDouble(c.getValue(), DataKeuangan::getPenjualan))));
        colPembelian.setCellValueFactory(c ->
                new SimpleStringProperty(Util.toRupiah(safeDouble(c.getValue(), DataKeuangan::getPembelian))));
        colStok.setCellValueFactory(c ->
                new SimpleStringProperty(Util.toRupiah(safeDouble(c.getValue(), DataKeuangan::getStokBarang))));
        colKeuntungan.setCellValueFactory(c ->
                new SimpleStringProperty(Util.toRupiah(safeDouble(c.getValue(), DataKeuangan::getKeuntungan))));
        colCreator.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue() != null && c.getValue().getCreator() != null ?
                                Optional.ofNullable(c.getValue().getCreator().getName()).orElse("-") : "-"
                ));

        // Enter di salah satu field = Simpan
        tfKeuntungan.setOnAction(e -> onSimpan());

        // load data awal
        loadData();
    }

    // ====== Actions ======
    @FXML private void onFilter() { loadData(); }

    @FXML private void onResetFilter() {
        dpStart.setValue(null);
        dpEnd.setValue(null);
        loadData();
    }

    @FXML private void onRefresh() { loadData(); }

    @FXML
    private void onSimpan() {
        // validasi sederhana
        var tgl = dpTanggal.getValue();
        if (tgl == null) { alert("Tanggal wajib diisi"); return; }

        double penjualan = parseDouble(tfPenjualan.getText());
        double pembelian = parseDouble(tfPembelian.getText());
        double stok = parseDouble(tfStok.getText());
        double keuntungan = parseDouble(tfKeuntungan.getText());

        btnSimpan.setDisable(true);

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                Http http = new Http();

                Map<String,Object> payload = new LinkedHashMap<>();
                payload.put("tanggal", iso.format(tgl));
                payload.put("penjualan", penjualan);
                payload.put("pembelian", pembelian);
                payload.put("stok_barang", stok);
                payload.put("keuntungan", keuntungan);

                String body = mapper.writeValueAsString(payload);
                // Ganti endpoint ini sesuai backend kamu (mis. "api/keuangan/create" jika berbeda)
                String resp = http.POST("api/keuangan", body);

                // Optional: cek status/message jika backend mengembalikan wrapper
                JsonNode node = mapper.readTree(resp);
                boolean status = node.path("status").asBoolean(false);
                if (!status) {
                    String msg = node.path("message").asText("Simpan Gagal.");
                    Util.alert(msg, AlertBox.Type.ERROR, 3, null);
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            Util.alert("Berhasil Simpan Data Keuangan", AlertBox.Type.SUCCESS, 3, null);
            btnSimpan.setDisable(false);
            clearForm();
            loadData();
        });
        task.setOnFailed(e -> {
            btnSimpan.setDisable(false);
            Throwable ex = task.getException();
            alert(ex != null && ex.getMessage() != null ? ex.getMessage() : "Gagal menyimpan data.");
        });

        new Thread(task, "post-keuangan").start();
    }

    @FXML
    private void onReset() {
        clearForm();
    }

    @FXML private void onCetak(javafx.event.ActionEvent evt) {
        var btn = (javafx.scene.control.Button) evt.getSource();
        btn.setDisable(true);

        // ambil filter
        final java.time.LocalDate start = dpStart.getValue();
        final java.time.LocalDate end   = dpEnd.getValue();

        javafx.concurrent.Task<java.nio.file.Path> task = new javafx.concurrent.Task<>() {
            @Override protected java.nio.file.Path call() throws Exception {
                // Fetch ulang sesuai filter (jangan ambil dari TableView supaya konsisten)
                org.util.Http http = new org.util.Http();
                // http.setBearerToken(Session.token()); // aktifkan jika endpoint butuh token

                java.util.Map<String,String> q = null;
                java.time.format.DateTimeFormatter iso = java.time.format.DateTimeFormatter.ISO_DATE;
                if (start != null || end != null) {
                    q = new java.util.HashMap<>();
                    if (start != null) q.put("date_start", start.format(iso));
                    if (end   != null) q.put("date_end",   end.format(iso));
                }

                String resp = http.GET("api/keuangan", q);
                var res = new DataKeuanganResponse(); // pakai kelas wrapper kamu
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.findAndRegisterModules();
                res = mapper.readValue(resp, DataKeuanganResponse.class);
                if (!res.isStatus()) throw new IllegalStateException(res.getMessage());

                java.util.List<org.models.DataKeuangan> rows =
                        res.getData() == null ? java.util.List.of() : res.getData();

                return exportTableToPdf(rows, start, end); // ⟵ kirim periode ke PDF
            }
        };

        task.setOnSucceeded(e -> {
            btn.setDisable(false);
            var out = task.getValue();
            info("PDF tersimpan di: " + out.toAbsolutePath().toString());
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { java.awt.Desktop.getDesktop().open(out.toFile()); } catch (Exception ignore) {}
            });
        });
        task.setOnFailed(e -> {
            btn.setDisable(false);
            Throwable ex = task.getException();
            alert(ex != null && ex.getMessage() != null ? ex.getMessage() : "Gagal export PDF.");
        });

        new Thread(task, "export-keuangan").start();
    }

    // ====== Helpers ======
    private void loadData() {
        LocalDate s = dpStart.getValue();
        LocalDate e = dpEnd.getValue();

        final String dateStart = (s != null) ? iso.format(s) : null;
        final String dateEnd   = (e != null) ? iso.format(e) : null;

        Task<List<DataKeuangan>> task = new Task<>() {
            @Override protected List<DataKeuangan> call() throws Exception {
                Http http = new Http();
                // http.setBearerToken(Session.token()); // aktifkan jika endpoint butuh token

                // Bangun query hanya kalau ada filter
                Map<String, String> q = null;
                if (dateStart != null || dateEnd != null) {
                    q = new java.util.HashMap<>();
                    if (dateStart != null) q.put("date_start", dateStart);
                    if (dateEnd   != null) q.put("date_end",   dateEnd);
                }

                String response = http.GET("api/keuangan", q); // q bisa null → tanpa filter
                DataKeuanganResponse res = mapper.readValue(response, DataKeuanganResponse.class);
                if (!res.isStatus()) throw new IllegalStateException(res.getMessage());
                return res.getData() != null ? res.getData() : java.util.Collections.emptyList();
            }
        };

        task.setOnSucceeded(ev -> table.getItems().setAll(task.getValue()));
        task.setOnFailed(ev -> {
            Throwable ex = task.getException();
            alert(ex != null && ex.getMessage() != null ? ex.getMessage() : "Gagal memuat data.");
        });

        new Thread(task, "get-keuangan").start();
    }

    private void info(String msg) {
        Util.alert(msg, AlertBox.Type.INFO, 3, null);
        // Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        // a.setHeaderText(null); a.setTitle("Info");
        // a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        // a.showAndWait();
    }

    private void clearForm() {
        dpTanggal.setValue(LocalDate.now(zone));
        tfPenjualan.clear();
        tfPembelian.clear();
        tfStok.clear();
        tfKeuntungan.clear();
    }

    private void numericOnly(TextField tf) {
        tf.textProperty().addListener((obs, old, val) -> {
            if (val == null) return;
            // izinkan digit dan titik desimal
            if (!val.matches("\\d*(\\.\\d*)?")) {
                tf.setText(val.replaceAll("[^\\d.]", ""));
            }
        });
    }

    private double parseDouble(String s) {
        try { return (s == null || s.isBlank()) ? 0d : Double.parseDouble(s); }
        catch (Exception e) { return 0d; }
    }

    private void alert(String msg) {
        Util.alert(msg, AlertBox.Type.ERROR, 3, null);
        // Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        // a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        // a.setHeaderText(null);
        // a.setTitle("Info");
        // a.showAndWait();
    }

    // safe helpers
    private static <T> String safe(T obj, java.util.function.Function<T,String> f) {
        try { return obj == null ? "-" : Optional.ofNullable(f.apply(obj)).orElse("-"); }
        catch (Exception e) { return "-"; }
    }
    private static <T> double safeDouble(T obj, java.util.function.ToDoubleFunction<T> f) {
        try { return obj == null ? 0d : f.applyAsDouble(obj); }
        catch (Exception e) { return 0d; }
    }

    private static final float MARGIN            = 36f;
    private static final float PDF_FONT_SIZE     = 10f;
    private static final float PDF_TITLE_SIZE    = 14f;
    private static final float PDF_ROW_LEADING   = 12f;  // jarak antar baris teks wrap
    private static final float PDF_ROW_GAP       = 8f;   // jarak antar baris tabel (ruang untuk separator)
    private static final float TEXT_TOP_PAD    = 2f;

    private int headersListIndex(String header) {
        return switch (header) {
            case "Tanggal"     -> 0;
            case "Penjualan"   -> 1;
            case "Pembelian"   -> 2;
            case "Stok Barang" -> 3;
            default            -> 4; // Keuntungan
        };
    }

    private String formatPeriode(java.time.LocalDate start, java.time.LocalDate end) {
        var loc = new java.util.Locale("id","ID");
        var shortFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", loc);
        if (start == null && end == null) return "Periode: Semua";
        if (start != null && end != null) return "Periode: " + start.format(shortFmt) + " – " + end.format(shortFmt);
        if (start != null) return "Periode: ≥ " + start.format(shortFmt);
        return "Periode: ≤ " + end.format(shortFmt);
    }

    private Path exportTableToPdf(java.util.List<org.models.DataKeuangan> rows, java.time.LocalDate start, java.time.LocalDate end) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font     = PDType1Font.HELVETICA;
            PDType1Font fontBold = PDType1Font.HELVETICA_BOLD;

            float pageW = PDRectangle.A4.getWidth();
            float pageH = PDRectangle.A4.getHeight();
            float contentW = pageW - 2 * MARGIN;

            // logo (opsional)
            PDImageXObject logo = null;
            try (InputStream is = getClass().getResourceAsStream("/org/assets/icon.png")) {
                if (is != null) logo = PDImageXObject.createFromByteArray(doc, is.readAllBytes(), "logo");
            }

            // kolom
            float wTanggal = 110f;
            float wOther   = (contentW - wTanggal) / 4f;
            final float[] widths = { wTanggal, wOther, wOther, wOther, wOther };
            final String[] headers = { "Tanggal", "Penjualan", "Pembelian", "Stok Barang", "Keuntungan" };

            // halaman pertama
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float x = MARGIN, y = pageH - MARGIN;

            // kop surat
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
            y -= 12f;

            // judul + periode
            cs.beginText(); cs.setFont(fontBold, 12f); cs.newLineAtOffset(x, y);
            cs.showText("Data Keuangan"); cs.endText();
            y -= 14f;

            cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(x, y);
            cs.showText(formatPeriode(start, end)); cs.endText();
            y -= 16f;

            // header kolom
            cs.setFont(fontBold, 10f);
            float cx = x;
            for (String h : headers) {
                cs.beginText(); cs.newLineAtOffset(cx + 2, y); cs.showText(h); cs.endText();
                cx += widths[headersListIndex(h)];
            }
            y -= 20f;
            cs.setFont(font, PDF_FONT_SIZE);

            // baris
            for (int i = 0; i < rows.size(); i++) {
                var r = rows.get(i);

                String vTanggal   = r.getTanggalFormatIndo();
                String vPenjualan = org.util.Util.toRupiah(r.getPenjualan());
                String vPembelian = org.util.Util.toRupiah(r.getPembelian());
                String vStok      = org.util.Util.toRupiah(r.getStokBarang());
                String vUntung    = org.util.Util.toRupiah(r.getKeuntungan());

                float contentH  = Math.max(18f, PDF_ROW_LEADING);
                float requiredH = contentH + PDF_ROW_GAP;

                if (y - requiredH < MARGIN) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageH - MARGIN;

                    // kop lagi
                    if (logo != null) cs.drawImage(logo, x, y - logoH, logoW, logoH);
                    tx = (logo != null) ? x + logoW + 10f : x;
                    cs.beginText(); cs.setFont(fontBold, PDF_TITLE_SIZE); cs.newLineAtOffset(tx, y - 14f);
                    cs.showText("PT QUDWAH BERKAH SEJAHTERA"); cs.endText();
                    cs.beginText(); cs.setFont(font, 11f); cs.newLineAtOffset(tx, y - 30f);
                    cs.showText("Bidang Usaha: Al-Qudwah"); cs.endText();
                    cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(tx, y - 44f);
                    cs.showText("Alamat: Jl. Maulana Hasanuddin, Kp. Cempa, Ds. Cilangkap, Kec. Kalanganyar - Kab. Lebak"); cs.endText();

                    y -= 56f; cs.moveTo(x, y); cs.lineTo(pageW - MARGIN, y); cs.setLineWidth(0.6f); cs.stroke();
                    y -= 12f;

                    // judul + periode lagi
                    cs.beginText(); cs.setFont(fontBold, 12f); cs.newLineAtOffset(x, y);
                    cs.showText("Data Keuangan"); cs.endText(); y -= 14f;

                    cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(x, y);
                    cs.showText(formatPeriode(start, end)); cs.endText(); y -= 16f;

                    // header ulang
                    cs.setFont(fontBold, 10f);
                    cx = x;
                    for (String h : headers) {
                        cs.beginText(); cs.newLineAtOffset(cx + 2, y); cs.showText(h); cs.endText();
                        cx += widths[headersListIndex(h)];
                    }
                    y -= 20f;
                    cs.setFont(font, PDF_FONT_SIZE);
                }

                // tulis satu baris
                float cy = y - TEXT_TOP_PAD;
                cx = x;
                String[] vals = { vTanggal, vPenjualan, vPembelian, vStok, vUntung };
                for (int k = 0; k < vals.length; k++) {
                    cs.beginText(); cs.newLineAtOffset(cx + 2, cy); cs.showText(vals[k]); cs.endText();
                    cx += widths[k];
                }

                // separator
                if (i != rows.size() - 1) {
                    float rowBottomY = y - contentH;
                    float sepY = rowBottomY + (PDF_ROW_GAP * 0.8f);
                    cs.setLineWidth(0.25f); cs.setStrokingColor(180);
                    cs.moveTo(x, sepY); cs.lineTo(pageW - MARGIN, sepY); cs.stroke();
                    cs.setStrokingColor(0);
                }

                y -= requiredH;
            }

            cs.close();

            // nama file ikut periode
            String fname;
            if (start != null || end != null) {
                var iso = java.time.format.DateTimeFormatter.ISO_DATE;
                String s = start != null ? start.format(iso) : "ALL";
                String e = end   != null ? end.format(iso)   : "ALL";
                fname = "keuangan-" + s + "_to_" + e + ".pdf";
            } else {
                fname = "keuangan-" + java.time.LocalDate.now() + ".pdf";
            }

            Path dir = Paths.get(System.getProperty("user.home"), "Documents");
            Files.createDirectories(dir);
            Path out = dir.resolve(fname);
            doc.save(out.toFile());
            return out;
        }
    }

    // ===== Response wrapper (kalau belum ada) =====
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataKeuanganResponse {
        private boolean status;
        private String message;
        private java.util.List<DataKeuangan> data;

        public boolean isStatus() { return status; }
        public String getMessage() { return message; }
        public java.util.List<DataKeuangan> getData() { return data; }
        public void setStatus(boolean status) { this.status = status; }
        public void setMessage(String message) { this.message = message; }
        public void setData(java.util.List<DataKeuangan> data) { this.data = data; }
    }
}
