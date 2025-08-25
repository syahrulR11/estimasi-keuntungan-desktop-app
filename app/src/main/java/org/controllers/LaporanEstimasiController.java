package org.controllers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;

import org.models.LaporanEstimasi;
import org.models.LaporanEstimasiResponse;
import org.models.PreProcessing;
import org.models.PreProcessingResponse;
import org.util.Http;
import org.util.Util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.components.AlertBox;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;


public class LaporanEstimasiController {

    // ====== FXML ======
    @FXML private DatePicker dpStart, dpEnd;
    @FXML private LineChart<String, Number> lcR2;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;
    @FXML private Button btnCetak;
    @FXML private ImageView imgBoxplot;
    @FXML private TableView<java.util.Map<String, String>> tvPre;
    @FXML private Label lblPreTotal;
    @FXML private GridPane cardGrid;

    @FXML private TableView<LaporanEstimasi> table;
    @FXML private TableColumn<LaporanEstimasi,String> colId, colTanggal, colIntercept,
            colKPenjualan, colKPembelian, colKStok,
            colPPenjualan, colPPembelian, colPStok,
            colActual, colEstimasi, colR2;

    private static final float MARGIN          = 36f;
    private static final float PDF_FONT_SIZE   = 9f;
    private static final float PDF_TITLE_SIZE  = 14f;
    private static final float PDF_ROW_LEADING = 12f;
    private static final float PDF_ROW_GAP     = 10f;
    private static final float TEXT_TOP_PAD    = 2f;

    // ====== State ======
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    private final DateTimeFormatter iso = DateTimeFormatter.ISO_DATE;
    @SuppressWarnings("deprecation")
    private final DateTimeFormatter dIndo = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("id","ID"));

    private static final String BASE = "api/model"; // endpoint GET data model

    @FXML
    public void initialize() {
        // y-axis 0..1
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(1);
        yAxis.setTickUnit(0.1);

        // tabel: formatter angka 4 desimal utk koef, 2 utk nilai rupiah (actual/estimasi), 3 utk r2
        numberCol(colIntercept, 5, LaporanEstimasi::getIntercept);
        numberCol(colKPenjualan, 5, LaporanEstimasi::getKoefPenjualan);
        numberCol(colKPembelian, 5, LaporanEstimasi::getKoefPembelian);
        numberCol(colKStok, 5, LaporanEstimasi::getKoefStokBarang);

        numberCol(colPPenjualan, 5, LaporanEstimasi::getPengaruhPenjualan);
        numberCol(colPPembelian, 5, LaporanEstimasi::getPengaruhPembelian);
        numberCol(colPStok, 5, LaporanEstimasi::getPengaruhStokBarang);

        numberCol(colActual, 2, LaporanEstimasi::getActualKeuntungan);
        numberCol(colEstimasi, 2, LaporanEstimasi::getEstimasiKeuntungan);
        numberCol(colR2, 3, LaporanEstimasi::getR2Score);

        colId.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getId() == null ? "-" : String.valueOf(c.getValue().getId())));
        colTanggal.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTanggal() == null ? "-" : c.getValue().getTanggal().format(dIndo)));

        // Atur grow & binding lebar 50% dari card (bukan layar)
        GridPane.setHgrow(imgBoxplot, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(lcR2,      javafx.scene.layout.Priority.ALWAYS);
        GridPane.setVgrow(imgBoxplot, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setVgrow(lcR2,       javafx.scene.layout.Priority.ALWAYS);

        imgBoxplot.setPreserveRatio(true);
        // width = (lebar card - hgap) / 2
        imgBoxplot.fitWidthProperty().bind(
            cardGrid.widthProperty().subtract(cardGrid.getHgap()).divide(2.0)
        );
        lcR2.prefWidthProperty().bind(
            cardGrid.widthProperty().subtract(cardGrid.getHgap()).divide(2.0)
        );

        lcR2.setMinHeight(240);

        // load data
        onRefresh();

        // reload jika tanggal berubah
        dpStart.valueProperty().addListener((o, ov, nv) -> onRefresh());
        dpEnd.valueProperty().addListener((o, ov, nv) -> onRefresh());

        loadPreprocessing();
        tvPre.getItems().addListener((ListChangeListener<Map<String,String>>) c -> updatePreTotal());
        updatePreTotal();
    }

    // helper set kolom numeric
    private interface DGetter { double get(LaporanEstimasi r); }
    private void numberCol(TableColumn<LaporanEstimasi,String> col, int scale, DGetter g) {
        col.setCellValueFactory(c -> {
            double v = g.get(c.getValue());
            String txt = Double.isNaN(v) ? "-" : String.format(Locale.US, "%." + scale + "f", v);
            return new SimpleStringProperty(txt);
        });
    }

    private void loadPreprocessing() {
        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception {
                Http http = new Http();

                // 1) GET JSON preprocessing
                String resp = http.GET(BASE+"/preprocessing", null);
                PreProcessingResponse pr = mapper.readValue(resp, PreProcessingResponse.class);
                if (!pr.isStatus()) throw new IllegalStateException(pr.getMessage() == null ? "Preprocessing gagal" : pr.getMessage());

                // 2) Muat gambar boxplot
                String url = BASE+"/preprocessing/image";
                byte[] pngBytes = http.GET_BYTES(url, null);   // <- ini KUNCI: bytes, bukan String URL

                javafx.scene.image.Image img =
                        new javafx.scene.image.Image(new java.io.ByteArrayInputStream(pngBytes));

                javafx.application.Platform.runLater(() -> {
                    imgBoxplot.setPreserveRatio(true);
                    // imgBoxplot.setFitWidth(900);
                    imgBoxplot.setImage(img);
                });

                // 3) Isi tabel preprocessing (kolom dinamis dari keys)
                java.util.List<PreProcessing> list = (pr.getData() == null)
                        ? java.util.List.of()
                        : pr.getData();

                // === mapping DTO -> Map<String,String> untuk TableView dinamis ===
                java.util.List<java.util.Map<String,String>> rows = new java.util.ArrayList<>(list.size());
                for (PreProcessing d : list) {
                    java.util.Map<String,String> m = new java.util.LinkedHashMap<>();
                    m.put("tanggal",                d.tanggal == null ? "" : d.tanggal);
                    m.put("penjualan",              fmtD(d.penjualan));
                    m.put("pembelian",              fmtD(d.pembelian));
                    m.put("stok_barang",            fmtD(d.stokBarang));
                    m.put("keuntungan",             fmtD(d.keuntungan));
                    if (d.keuntunganSmoothed != null) {
                        m.put("keuntungan_smoothed", fmtD(d.keuntunganSmoothed));
                    }
                    rows.add(m);
                }

                // === tampilkan di UI ===
                Platform.runLater(() -> {
                    buildDynamicColumns(tvPre, rows);
                    tvPre.getItems().setAll(rows);
                    updatePreTotal();
                });

                return null;
            }
        };

        t.setOnFailed(e -> showError(t.getException() != null ? t.getException().getMessage() : "Gagal memuat preprocessing"));
        new Thread(t, "load-preprocessing").start();
    }

    private static String fmtD(Double v) {
        return v == null ? "" : String.format(java.util.Locale.US, "%.2f", v);
    }

    private void buildDynamicColumns(
            TableView<java.util.Map<String,String>> tv,
            java.util.List<java.util.Map<String,String>> rows
    ) {
        tv.getColumns().clear();
        if (rows == null || rows.isEmpty()) return;

        // urutkan keys stabil dari baris pertama
        var keys = new java.util.ArrayList<>(rows.get(0).keySet());
        for (String key : keys) {
            TableColumn<java.util.Map<String,String>, String> col = new TableColumn<>(key);
            col.setCellValueFactory(p -> new SimpleStringProperty(p.getValue().getOrDefault(key, "")));
            col.setPrefWidth(Math.max(100, key.length() * 9.0)); // perkiraan lebar
            tv.getColumns().add(col);
        }
    }

    private void updatePreTotal() {
        if (lblPreTotal == null || tvPre == null) return;
        int n = tvPre.getItems() == null ? 0 : tvPre.getItems().size();
        lblPreTotal.setText("Total data: " + n);
    }

    @FXML
    private void onRefresh() {
        LocalDate s = dpStart.getValue();
        LocalDate e = dpEnd.getValue();
        final String qs = (s != null) ? iso.format(s) : null;
        final String qe   = (e != null) ? iso.format(e) : null;

        Task<List<LaporanEstimasi>> task = new Task<>() {
            @Override protected List<LaporanEstimasi> call() throws Exception {
                Http http = new Http();
                Map<String, String> q = null;
                if (qs != null || qe != null) {
                    q = new java.util.HashMap<>();
                    if (qs != null) q.put("date_start", qs);
                    if (qe   != null) q.put("date_end",   qe);
                }
                String resp = http.GET(BASE, q);
                LaporanEstimasiResponse r = mapper.readValue(resp, LaporanEstimasiResponse.class);
                if (!r.isStatus()) throw new IllegalStateException(r.getMessage());
                var list = r.getData() == null ? java.util.List.<LaporanEstimasi>of() : r.getData();
                // urutkan by tanggal ASC
                // return list.stream().sorted(Comparator.comparing(LaporanEstimasi::getTanggal)).toList();
                return list;
            }
        };

        task.setOnSucceeded(evv -> {
            List<LaporanEstimasi> rows = task.getValue();
            table.getItems().setAll(rows);
            plotR2(rows.stream().sorted(Comparator.comparing(LaporanEstimasi::getTanggal)).toList());
        });
        task.setOnFailed(evv -> {
            Throwable ex = task.getException();
            showError(ex != null && ex.getMessage() != null ? ex.getMessage() : "Gagal memuat data model.");
        });

        new Thread(task, "get-model-metrics").start();
    }

    @FXML
    private void onCetak(javafx.event.ActionEvent evt) {
        Button trigger = (Button) evt.getSource();
        trigger.setDisable(true);

        // Snapshot tabel sesuai filter yang sedang tampil
        var snapshot = new java.util.ArrayList<>(table.getItems());
        LocalDate s = dpStart.getValue();
        LocalDate e = dpEnd.getValue();

        Task<Path> task = new Task<>() {
            @Override protected Path call() throws Exception { return exportModelToPdf(snapshot, s, e); }
        };
        task.setOnSucceeded(ev -> {
            trigger.setDisable(false);
            Path out = task.getValue();
            showInfo("PDF tersimpan di: " + out.toAbsolutePath().toString());
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { java.awt.Desktop.getDesktop().open(out.toFile()); } catch (Exception ignore) {}
            });
        });
        task.setOnFailed(ev -> {
            trigger.setDisable(false);
            Throwable ex = task.getException();
            showError(ex != null && ex.getMessage() != null ? ex.getMessage() : "Gagal export PDF.");
        });
        new Thread(task, "export-model").start();
    }

    // isi line chart
    private void plotR2(List<LaporanEstimasi> rows) {
        lcR2.setAnimated(false);
        lcR2.getData().clear();
        if (rows == null || rows.isEmpty()) return;

        // 1) Kategori = tanggal yang ADA di data (urut ASC)
        List<String> labels = rows.stream()
                .map(LaporanEstimasi::getTanggal)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .map(d -> d.format(dIndo))
                .toList();
        xAxis.getCategories().setAll(labels);

        // 2) Seri data: gunakan label tanggal sebagai X
        var s = new javafx.scene.chart.XYChart.Series<String, Number>();
        s.setName("R²");
        for (var r : rows) {
            if (r.getTanggal() == null) continue;
            String label = r.getTanggal().format(dIndo);
            s.getData().add(new javafx.scene.chart.XYChart.Data<>(label, r.getR2Score()));
        }
        lcR2.getData().add(s);

        // Opsional: rotasi label jika banyak
        if (labels.size() > 8) xAxis.setTickLabelRotation(45);
    }

    @SuppressWarnings("null")
    private Path exportModelToPdf(List<org.models.LaporanEstimasi> rows, LocalDate start, LocalDate end) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font     = PDType1Font.HELVETICA;
            PDType1Font fontBold = PDType1Font.HELVETICA_BOLD;

            // Landscape A4
            float pageW = PDRectangle.A4.getHeight();  // 842
            float pageH = PDRectangle.A4.getWidth();   // 595
            PDRectangle LAND = new PDRectangle(pageW, pageH);

            // Lebar kolom (total <= contentW ~ 770)
            final float[] widths = {
                22f, 70f, 50f, 60f, 60f, 70f, 70f, 70f, 70f, 80f, 80f, 38f
            };
            // Header sesuai permintaan
            final String[] headers = {
                "id","tanggal","intercept","koef_penjualan","koef_pembelian","koef_stok_barang",
                "pengaruh_penjualan","pengaruh_pembelian","pengaruh_stok_barang",
                "actual_keuntungan","estimasi_keuntungan","r2_score"
            };

            // Logo (opsional)
            PDImageXObject logo = null;
            try (InputStream is = getClass().getResourceAsStream("/org/assets/icon.png")) {
                if (is != null) logo = PDImageXObject.createFromByteArray(doc, is.readAllBytes(), "logo");
            }

            // --- halaman pertama ---
            PDPage page = new PDPage(LAND); doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float x = MARGIN, y = pageH - MARGIN;

            // ==== Kop Surat ====
            float logoW = 32f, logoH = 32f;
            if (logo != null) cs.drawImage(logo, x, y - logoH, logoW, logoH);
            float tx = (logo != null) ? x + logoW + 10f : x;

            cs.beginText(); cs.setFont(fontBold, PDF_TITLE_SIZE); cs.newLineAtOffset(tx, y - 12f);
            cs.showText("PT QUDWAH BERKAH SEJAHTERA"); cs.endText();

            cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(tx, y - 28f);
            cs.showText("Bidang Usaha: Al-Qudwah"); cs.endText();

            cs.beginText(); cs.setFont(font, 9f); cs.newLineAtOffset(tx, y - 40f);
            cs.showText("Alamat: Jl. Maulana Hasanuddin, Kp. Cempa, Ds. Cilangkap, Kec. Kalanganyar - Kab. Lebak"); cs.endText();

            y -= 52f; cs.moveTo(x, y); cs.lineTo(pageW - MARGIN, y); cs.setLineWidth(0.6f); cs.stroke();
            y -= 12f;

            // ==== Judul + periode + statistik R² ====
            var loc = new Locale("id","ID");
            var fmtPer = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", loc);
            String periode = (start==null && end==null) ? "Periode: Semua"
                    : (start!=null && end!=null) ? "Periode: " + start.format(fmtPer) + " – " + end.format(fmtPer)
                    : (start!=null) ? "Periode: ≥ " + start.format(fmtPer)
                    : "Periode: ≤ " + end.format(fmtPer);

            // Statistik R²
            java.util.DoubleSummaryStatistics st = rows.stream().mapToDouble(org.models.LaporanEstimasi::getR2Score).summaryStatistics();
            String r2line = (st.getCount() == 0)
                    ? "R²: -"
                    : String.format(java.util.Locale.US, "R²: min %.3f  |  avg %.3f  |  max %.3f",
                        st.getMin(), st.getAverage(), st.getMax());

            cs.beginText(); cs.setFont(fontBold, 12f); cs.newLineAtOffset(x, y);
            cs.showText("Rangkuman Model & Koefisien"); cs.endText(); y -= 14f;

            cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(x, y);
            cs.showText(periode); cs.endText(); y -= 12f;

            cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(x, y);
            cs.showText(r2line); cs.endText(); y -= 16f;

            // ==== Header kolom ====
            cs.setFont(fontBold, 9f);
            float cx = x;
            for (String h : headers) {
                cs.beginText(); cs.newLineAtOffset(cx + 2, y); cs.showText(h); cs.endText();
                cx += widths[Math.min(headers.length-1, java.util.Arrays.asList(headers).indexOf(h))];
            }
            y -= 18f;
            cs.setFont(font, PDF_FONT_SIZE);

            // ===== Data baris =====
            for (int i = 0; i < rows.size(); i++) {
                var r = rows.get(i);

                String vId   = r.getId() == null ? "-" : String.valueOf(r.getId());
                String vTgl  = r.getTanggal() == null ? "-" : r.getTanggal().format(fmtPer);
                String vInt  = String.format(java.util.Locale.US, "%.2f", r.getIntercept());

                String vKPen = String.format(java.util.Locale.US, "%.4f", r.getKoefPenjualan());
                String vKPem = String.format(java.util.Locale.US, "%.4f", r.getKoefPembelian());
                String vKSto = String.format(java.util.Locale.US, "%.4f", r.getKoefStokBarang());

                String vPPen = String.format(java.util.Locale.US, "%.4f", r.getPengaruhPenjualan());
                String vPPem = String.format(java.util.Locale.US, "%.4f", r.getPengaruhPembelian());
                String vPSto = String.format(java.util.Locale.US, "%.4f", r.getPengaruhStokBarang());

                String vAct  = org.util.Util.toRupiah(r.getActualKeuntungan());
                String vEst  = org.util.Util.toRupiah(r.getEstimasiKeuntungan());
                String vR2   = String.format(java.util.Locale.US, "%.3f", r.getR2Score());

                float contentH  = Math.max(14f, PDF_ROW_LEADING);
                float requiredH = contentH + PDF_ROW_GAP;

                // page break
                if (y - requiredH < MARGIN) {
                    cs.close();
                    page = new PDPage(LAND); doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageH - MARGIN;

                    // kop singkat
                    if (logo != null) cs.drawImage(logo, x, y - logoH, logoW, logoH);
                    tx = (logo != null) ? x + logoW + 10f : x;
                    cs.beginText(); cs.setFont(fontBold, PDF_TITLE_SIZE); cs.newLineAtOffset(tx, y - 12f);
                    cs.showText("PT QUDWAH BERKAH SEJAHTERA"); cs.endText();
                    y -= 24f; cs.moveTo(x, y); cs.lineTo(pageW - MARGIN, y); cs.setLineWidth(0.6f); cs.stroke();
                    y -= 10f;

                    // judul & periode lagi
                    cs.beginText(); cs.setFont(fontBold, 12f); cs.newLineAtOffset(x, y);
                    cs.showText("Rangkuman Model & Koefisien"); cs.endText(); y -= 14f;
                    cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(x, y);
                    cs.showText(periode + "  |  " + r2line); cs.endText(); y -= 14f;

                    // header kolom
                    cs.setFont(fontBold, 9f);
                    cx = x;
                    for (int h = 0; h < headers.length; h++) {
                        cs.beginText(); cs.newLineAtOffset(cx + 2, y); cs.showText(headers[h]); cs.endText();
                        cx += widths[h];
                    }
                    y -= 18f;
                    cs.setFont(font, PDF_FONT_SIZE);
                }

                // tulis satu baris
                float cy = y - TEXT_TOP_PAD; cx = x;
                String[] vals = { vId, vTgl, vInt, vKPen, vKPem, vKSto, vPPen, vPPem, vPSto, vAct, vEst, vR2 };
                for (int k = 0; k < vals.length; k++) {
                    cs.beginText(); cs.newLineAtOffset(cx + 2, cy); cs.showText(vals[k]); cs.endText();
                    cx += widths[k];
                }

                // separator lembut di tengah gap
                if (i != rows.size() - 1) {
                    float rowBottomY = y - contentH;
                    float sepY = rowBottomY + (PDF_ROW_GAP * 0.6f);
                    cs.setLineWidth(0.25f); cs.setStrokingColor(180);
                    cs.moveTo(x, sepY); cs.lineTo(pageW - MARGIN, sepY); cs.stroke();
                    cs.setStrokingColor(0);
                }

                y -= requiredH;
            }

            cs.close();

            // nama file ikut periode
            var iso = java.time.format.DateTimeFormatter.ISO_DATE;
            String s = start != null ? start.format(iso) : "ALL";
            String e = end   != null ? end.format(iso)   : "ALL";
            String fname = "model-" + s + "_to_" + e + ".pdf";

            Path dir = Paths.get(System.getProperty("user.home"), "Documents");
            Files.createDirectories(dir);
            Path out = dir.resolve(fname);
            doc.save(out.toFile());
            return out;
        }
    }

    // ===== ui helpers =====
    private void showInfo(String msg) {
        Util.alert(msg, AlertBox.Type.INFO, 3, null);
        // Platform.runLater(() -> {
        //     var a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        //     a.setHeaderText(null); a.setTitle("Info"); a.showAndWait();
        // });
    }
    private void showError(String msg) {
        Util.alert(msg, AlertBox.Type.ERROR, 3, null);
        // Platform.runLater(() -> {
        //     var a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        //     a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
        // });
    }
}
