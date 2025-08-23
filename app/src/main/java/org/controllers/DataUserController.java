package org.controllers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.models.User;
import org.models.Role;
import org.models.RoleResponse;
import org.util.Http;
import org.util.Util;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.components.AlertBox;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DataUserController {

    // ====== FXML ======
    @FXML private TextField tfId, tfName, tfUsername, tfEmail, tfPhone, tfCari;
    @FXML private PasswordField pfPassword, pfConfirm;
    @FXML private ComboBox<Role> cbRole;
    @FXML private ComboBox<String> cbGender;
    @FXML private DatePicker dpBirth;
    @FXML private TextArea taAddress;
    @FXML private Button btnCetak, btnSimpan, btnUbah, btnHapus, btnReset;
    @FXML private VBox root;
    @FXML private ComboBox<Role> cbRoleFilter;

    @FXML private TableView<User> table;
    @FXML private TableColumn<User,String> colId, colName, colUsername, colEmail, colRole, colCreated;
    
    // ====== State ======
    private List<User> masterUsers = new ArrayList<>();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    private static final String BASE_USER = "api/user";
    private static final String BASE_ROLE = "api/role";

    // PDF constants
    private static final float MARGIN          = 36f;
    private static final float PDF_FONT_SIZE   = 10f;
    private static final float PDF_TITLE_SIZE  = 14f;
    private static final float PDF_ROW_LEADING = 12f;
    private static final float PDF_ROW_GAP     = 12f;
    private static final float TEXT_TOP_PAD    = 2f;

    // ====== Init ======
    @FXML
    public void initialize() {
        // Combo gender
        cbGender.getItems().setAll("Laki-Laki", "Perempuan");

        // Tabel
        colId.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getId() == null ? "-" : String.valueOf(c.getValue().getId())));
        colName.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getName())));
        colUsername.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getUsername())));
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getEmail())));
        colRole.setCellValueFactory(c -> new SimpleStringProperty(nz(c.getValue().getRoleName())));
        DateTimeFormatter d = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        colCreated.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCreatedAt() == null ? "-" : c.getValue().getCreatedAt().toLocalDate().format(d)));

        // pilih baris -> isi form
        table.getSelectionModel().selectedItemProperty().addListener((o, old, r) -> fillForm(r));

        // cari lokal
        cbRoleFilter.valueProperty().addListener((obs, old, val) -> applyClientFilter()); // ⟵ bukan loadTable
        tfCari.textProperty().addListener((obs, o, q) -> applyClientFilter());

        // muat data
        loadRoles();
        loadTable();
    }

    // ====== Actions ======
    @FXML private void onRefresh() { loadTable(); }

    @FXML private void onReset() {
        clearForm();
        table.getSelectionModel().clearSelection();
    }

    @FXML private void onSimpan() {
        String name = t(tfName), username = t(tfUsername), email = t(tfEmail);
        if (name.isEmpty() || username.isEmpty() || email.isEmpty()) { alert("Nama/username/email wajib diisi."); return; }
        Role r = cbRole.getValue(); Integer roleId = r == null ? null : r.getId();

        String password = requireNewPasswordOrAlert();   // ← WAJIB untuk create
        if (password == null) return;

        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("username", username);
        payload.put("email", email);
        payload.put("phone", t(tfPhone));
        payload.put("role_id", roleId);
        payload.put("gender", cbGender.getValue());
        payload.put("birth_date", dpBirth.getValue() == null ? null : dpBirth.getValue().toString());
        payload.put("address", taAddress.getText());
        payload.put("password", password);               // ← kirim password

        doRequest("POST", BASE_USER, payload, "Berhasil menyimpan user.");
    }

    @FXML private void onUbah() {
        Integer id = parseId();
        if (id == null) { alert("Pilih data di tabel untuk diubah."); return; }
        String name = t(tfName), username = t(tfUsername), email = t(tfEmail);
        if (name.isEmpty() || username.isEmpty() || email.isEmpty()) { alert("Nama/username/email wajib diisi."); return; }
        Role r = cbRole.getValue(); Integer roleId = r == null ? null : r.getId();

        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("username", username);
        payload.put("email", email);
        payload.put("phone", t(tfPhone));
        payload.put("role_id", roleId);
        payload.put("gender", cbGender.getValue());
        payload.put("birth_date", dpBirth.getValue() == null ? null : dpBirth.getValue().toString());
        payload.put("address", taAddress.getText());

        String password = optionalPasswordOrAlert();     // ← hanya jika diisi
        if (password == null && (!isBlank(pfPassword) || !isBlank(pfConfirm))) return;
        if (password != null) payload.put("password", password);

        doRequest("PUT", BASE_USER + "/" + id, payload, "Berhasil mengubah user.");
    }

    private boolean isBlank(TextField tf) { String s = tf.getText(); return s == null || s.isBlank(); }

    @FXML private void onHapus() {
        Integer id = parseId();
        if (id == null) { alert("Pilih data di tabel untuk dihapus."); return; }
        if (!confirm("Hapus user terpilih?")) return;
        doRequest("DELETE", BASE_USER + "/" + id, null, "Berhasil menghapus user.");
    }

    // Export PDF (snapshot & background)
    @FXML
    private void onCetak(javafx.event.ActionEvent evt) {
        Button trigger = (Button) evt.getSource();
        trigger.setDisable(true);

        List<User> snapshot = new ArrayList<>(table.getItems());
        String roleName = getSelectedRoleName();

        Task<Path> task = new Task<>() {
            @Override protected Path call() throws Exception {
                return exportUsersToPdf(snapshot, roleName); // signature sudah support role name
            }
        };
        task.setOnSucceeded(e -> {
            trigger.setDisable(false);
            Path out = task.getValue();
            info("PDF tersimpan di: " + out.toAbsolutePath());
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try { java.awt.Desktop.getDesktop().open(out.toFile()); } catch (Exception ignore) {}
            });
        });
        task.setOnFailed(e -> {
            trigger.setDisable(false);
            Throwable ex = task.getException();
            alert(ex != null && ex.getMessage() != null ? ex.getMessage() : "Gagal export PDF.");
        });
        new Thread(task, "export-users").start();
    }

    // ====== Core ======
    private void loadTable() {
        Task<List<User>> task = new Task<>() {
            @Override protected List<User> call() throws Exception {
                Http http = new Http();
                // Ambil semua dari server; kalau endpoint mendukung role_id juga boleh tambahkan Map query
                String resp = http.GET(BASE_USER, null);
                UserListResponse r = mapper.readValue(resp, UserListResponse.class);
                if (!r.isStatus()) throw new IllegalStateException(r.getMessage());
                return r.getData() == null ? List.of() : r.getData();
            }
        };
        task.setOnSucceeded(e -> { 
            masterUsers = task.getValue();
            applyClientFilter(); // terapkan filter role + teks
        });
        task.setOnFailed(e -> alert(errMsg(task.getException(), "Gagal memuat data.")));
        new Thread(task, "get-users").start();
    }

    private void loadRoles() {
        Task<List<Role>> task = new Task<>() {
            @Override protected List<Role> call() throws Exception {
                Http http = new Http();
                String resp = http.GET(BASE_ROLE, null);
                RoleResponse r = mapper.readValue(resp, RoleResponse.class);
                if (!r.isStatus()) throw new IllegalStateException(r.getMessage());
                return r.getData() == null ? List.of() : r.getData();
            }
        };
        task.setOnSucceeded(e -> {
            List<Role> roles = task.getValue();
            // untuk form
            cbRole.getItems().setAll(roles);

            // untuk filter: tambah opsi "Semua Role" (id null)
            Role ALL = new Role(); ALL.setId(null); ALL.setName("Semua Role");
            cbRoleFilter.getItems().setAll(ALL); // masukkan dulu ALL
            cbRoleFilter.getItems().addAll(roles);
            cbRoleFilter.setValue(ALL); // default

            // render nama untuk kedua combo
            java.util.function.Consumer<ComboBox<Role>> decorate = (cb) -> {
                cb.setCellFactory(list -> new ListCell<>() {
                    @Override protected void updateItem(Role item, boolean empty) {
                        super.updateItem(item, empty); setText(empty || item == null ? null : item.getName());
                    }
                });
                cb.setButtonCell(new ListCell<>() {
                    @Override protected void updateItem(Role item, boolean empty) {
                        super.updateItem(item, empty); setText(empty || item == null ? null : item.getName());
                    }
                });
            };
            decorate.accept(cbRole);
            decorate.accept(cbRoleFilter);
        });
        task.setOnFailed(e -> alert(errMsg(task.getException(), "Gagal memuat role.")));
        new Thread(task, "get-roles").start();
    }

    private void doRequest(String method, String path, Map<String,Object> body, String successMsg) {
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                Http http = new Http();
                String json = body == null ? "{}" : mapper.writeValueAsString(body);
                String resp;
                switch (method) {
                    // NOTE: ganti ke http.PUT / http.DELETE jika util sudah ada
                    case "POST" -> resp = http.POST(path, json);
                    case "PUT"  -> resp = http.PUT(path, json);    // override kalau backend support
                    case "DELETE" -> resp = http.DELETE(path);  // idem
                    default -> throw new IllegalArgumentException("Method tidak didukung");
                }
                UserResponse r = mapper.readValue(resp, UserResponse.class);
                if (!r.isStatus()) throw new IllegalStateException(r.getMessage());
                return null;
            }
        };
        task.setOnSucceeded(e -> { info(successMsg); clearForm(); loadTable(); });
        task.setOnFailed(e -> alert(errMsg(task.getException(), "Operasi gagal.")));
        new Thread(task, "user-" + method.toLowerCase()).start();
    }

    // ====== PDF ======
    private Path exportUsersToPdf(List<User> rows, String roleFilterName) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDType1Font font     = PDType1Font.HELVETICA;
            PDType1Font fontBold = PDType1Font.HELVETICA_BOLD;

            float pageW = PDRectangle.A4.getWidth();
            float pageH = PDRectangle.A4.getHeight();
            float contentW = pageW - 2 * MARGIN;

            // kolom & header sama seperti sebelumnya...
            float[] widths = { 30f, 110f, 80f, 140f, 100f, contentW - (30f+110f+80f+140f+100f) };
            String[] headers = { "ID", "Nama", "Username", "Email", "Role", "Dibuat" };

            // logo (opsional)
            PDImageXObject logo = null;
            try (InputStream is = getClass().getResourceAsStream("/org/assets/icon.png")) {
                if (is != null) logo = PDImageXObject.createFromByteArray(doc, is.readAllBytes(), "logo");
            }

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);

            float x = MARGIN, y = pageH - MARGIN;

            // kop surat … (tetap sama)
            float logoW = 36f, logoH = 36f;
            if (logo != null) cs.drawImage(logo, x, y - logoH, logoW, logoH);
            float tx = (logo != null) ? x + logoW + 10f : x;

            cs.beginText(); cs.setFont(fontBold, PDF_TITLE_SIZE); cs.newLineAtOffset(tx, y - 14f);
            cs.showText("PT QUDWAH BERKAH SEJAHTERA"); cs.endText();
            cs.beginText(); cs.setFont(font, 11f); cs.newLineAtOffset(tx, y - 30f);
            cs.showText("Bidang Usaha: Al-Qudwah"); cs.endText();
            cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(tx, y - 44f);
            cs.showText("Alamat: Jl. Maulana Hasanuddin, Kp. Cempa, Ds. Cilangkap, Kec. Kalanganyar - Kab. Lebak"); cs.endText();

            y -= 56f; cs.moveTo(x, y); cs.lineTo(pageW - MARGIN, y); cs.setLineWidth(0.6f); cs.stroke();
            y -= 12f;

            // judul + filter role
            cs.beginText(); cs.setFont(fontBold, 12f); cs.newLineAtOffset(x, y);
            cs.showText("Daftar User"); cs.endText(); 
            y -= 14f;

            cs.beginText(); cs.setFont(font, 10f); cs.newLineAtOffset(x, y);
            cs.showText("Filter Role: " + (roleFilterName == null ? "Semua Role" : roleFilterName));
            cs.endText();
            y -= 16f;

            // header kolom
            cs.setFont(fontBold, 10f);
            float cx = x; 
            for (int i = 0; i < headers.length; i++) {
                cs.beginText(); cs.newLineAtOffset(cx + 2, y); cs.showText(headers[i]); cs.endText();
                cx += widths[i];
            }
            y -= 20f;
            cs.setFont(font, PDF_FONT_SIZE);

            // helper wrap nama & email
            java.util.function.Function<String, Float> strW = (s) -> {
                String t = s == null ? "" : s;
                try { return font.getStringWidth(t) / 1000f * PDF_FONT_SIZE; }
                catch (Exception e) { return (float)(t.length() * 0.55 * PDF_FONT_SIZE); }
            };
            java.util.function.BiFunction<String, Float, List<String>> wrap = (text, width) -> {
                List<String> lines = new ArrayList<>();
                if (text == null || text.isBlank()) { lines.add(""); return lines; }
                String[] words = text.split("\\s+");
                StringBuilder cur = new StringBuilder();
                float maxW = width - 4f;
                for (String w : words) {
                    String probe = (cur.length()==0) ? w : (cur + " " + w);
                    if (strW.apply(probe) > maxW) { if (cur.length()>0) lines.add(cur.toString()); cur = new StringBuilder(w); }
                    else cur = new StringBuilder(probe);
                }
                if (cur.length()>0) lines.add(cur.toString());
                return lines;
            };

            for (int i = 0; i < rows.size(); i++) {
                User u = rows.get(i);
                String vId   = u.getId() == null ? "-" : String.valueOf(u.getId());
                String vName = nz(u.getName());
                String vUser = nz(u.getUsername());
                String vMail = nz(u.getEmail());
                String vRole = nz(u.getRoleName());
                String vDate = u.getCreatedAt() == null ? "-" : u.getCreatedAt().toLocalDate().toString();

                // hitung tinggi (wrap nama & email)
                List<String> linesName  = wrap.apply(vName, widths[1]);
                List<String> linesEmail = wrap.apply(vMail, widths[3]);
                int maxLines = Math.max(Math.max(1, linesName.size()), Math.max(1, linesEmail.size()));
                float contentH  = Math.max(18f, maxLines * PDF_ROW_LEADING);
                float requiredH = contentH + PDF_ROW_GAP;

                // page break
                if (y - requiredH < MARGIN) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4); doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pageH - MARGIN;

                    // kop
                    if (logo != null) cs.drawImage(logo, x, y - logoH, logoW, logoH);
                    tx = (logo != null) ? x + logoW + 10f : x;
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, PDF_TITLE_SIZE); cs.newLineAtOffset(tx, y - 14f);
                    cs.showText("PT QUDWAH BERKAH SEJAHTERA"); cs.endText();
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 11f); cs.newLineAtOffset(tx, y - 30f);
                    cs.showText("Bidang Usaha: Al-Qudwah"); cs.endText();
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f); cs.newLineAtOffset(tx, y - 44f);
                    cs.showText("Alamat: Jl. Maulana Hasanuddin, Kp. Cempa, Ds. Cilangkap, Kec. Kalanganyar - Kab. Lebak"); cs.endText();
                    y -= 56f; cs.moveTo(x, y); cs.lineTo(pageW - MARGIN, y); cs.setLineWidth(0.6f); cs.stroke();
                    y -= 16f;
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA_BOLD, 12f); cs.newLineAtOffset(x, y);
                    cs.showText("Daftar User"); cs.endText(); y -= 18f;
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 10f);
                    cx = x; for (int h = 0; h < headers.length; h++) {
                        cs.beginText(); cs.newLineAtOffset(cx + 2, y); cs.showText(headers[h]); cs.endText();
                        cx += widths[h];
                    }
                    y -= 20f; cs.setFont(PDType1Font.HELVETICA, PDF_FONT_SIZE);
                }

                // tulis baris
                float cy = y - TEXT_TOP_PAD; cx = x;

                // ID (single)
                cs.beginText(); cs.newLineAtOffset(cx + 2, cy); cs.showText(vId); cs.endText(); cx += widths[0];

                // Nama (wrap)
                float nx = cx; float ny = cy; 
                for (String ln : linesName) {
                    cs.beginText(); cs.newLineAtOffset(nx + 2, ny); cs.showText(ln); cs.endText();
                    ny -= PDF_ROW_LEADING;
                }
                cx += widths[1];

                // Username (single)
                cs.beginText(); cs.newLineAtOffset(cx + 2, cy); cs.showText(vUser); cs.endText(); cx += widths[2];

                // Email (wrap)
                float ex = cx; float ey = cy;
                for (String le : linesEmail) {
                    cs.beginText(); cs.newLineAtOffset(ex + 2, ey); cs.showText(le); cs.endText();
                    ey -= PDF_ROW_LEADING;
                }
                cx += widths[3];

                // Role (single)
                cs.beginText(); cs.newLineAtOffset(cx + 2, cy); cs.showText(vRole); cs.endText(); cx += widths[4];

                // Dibuat (single)
                cs.beginText(); cs.newLineAtOffset(cx + 2, cy); cs.showText(vDate); cs.endText();

                // separator di tengah gap
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
            String safeName = (roleFilterName == null || roleFilterName.isBlank()) ? "Semua" : roleFilterName.replaceAll("\\s+","_");
            Path dir = Paths.get(System.getProperty("user.home"), "Documents");
            Files.createDirectories(dir);
            Path out = dir.resolve("users-role-" + safeName + "-" + LocalDate.now() + ".pdf");
            doc.save(out.toFile());
            return out;
        }
    }

    // ===== Helpers & wrappers =====
    private void fillForm(User u) {
        if (u == null) return;
        tfId.setText(u.getId() == null ? "" : String.valueOf(u.getId()));
        tfName.setText(nz(u.getName()));
        tfUsername.setText(nz(u.getUsername()));
        tfEmail.setText(nz(u.getEmail()));
        tfPhone.setText(nz(u.getPhone()));
        cbGender.setValue(u.getGender());
        dpBirth.setValue(u.getBirthDate());
        taAddress.setText(nz(u.getAddress()));
        // pilih role by id
        if (u.getRoleId() != null) {
            for (Role r : cbRole.getItems()) if (u.getRoleId().equals(r.getId())) { cbRole.setValue(r); break; }
        } else cbRole.setValue(null);
    }
    private void clearForm() {
        for (TextField tf : List.of(tfId, tfName, tfUsername, tfEmail, tfPhone)) tf.clear();
        cbRole.setValue(null); cbGender.setValue(null); dpBirth.setValue(null); taAddress.clear();
        pfPassword.clear(); pfConfirm.clear();
    }
    private void applyClientFilter() {
        Integer roleId = getSelectedRoleId();
        String q = tfCari.getText();
        String needle = (q == null) ? "" : q.toLowerCase(Locale.ROOT);

        List<User> filtered = masterUsers.stream()
            // filter role (jika dipilih)
            .filter(u -> roleId == null || Objects.equals(u.getRoleId(), roleId))
            // filter teks lokal
            .filter(u ->
                needle.isBlank() ||
                nz(u.getName()).toLowerCase(Locale.ROOT).contains(needle) ||
                nz(u.getUsername()).toLowerCase(Locale.ROOT).contains(needle) ||
                nz(u.getEmail()).toLowerCase(Locale.ROOT).contains(needle) ||
                nz(u.getRoleName()).toLowerCase(Locale.ROOT).contains(needle)
            )
            .toList();

        table.getItems().setAll(filtered);
    }
    private Integer getSelectedRoleId() {
        if (cbRoleFilter == null) return null;
        var r = cbRoleFilter.getValue();
        return (r == null) ? null : r.getId(); // null artinya "Semua Role"
    }
    private String getSelectedRoleName() {
        if (cbRoleFilter == null || cbRoleFilter.getValue() == null || cbRoleFilter.getValue().getId() == null)
            return "Semua Role";
        return cbRoleFilter.getValue().getName();
    }

    private Integer parseId() {
        try { String s = tfId.getText(); return (s==null||s.isBlank()) ? null : Integer.parseInt(s.trim()); }
        catch (Exception e) { return null; }
    }
    private String t(TextField tf) { return tf.getText()==null ? "" : tf.getText().trim(); }
    private String nz(String s) { return s == null ? "" : s; }

    private void info(String msg) {
        Util.alert(msg, AlertBox.Type.INFO, 3, null);
        // var a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        // a.setHeaderText(null); a.setTitle("Info"); a.showAndWait();
    }
    private void alert(String msg) {
        Util.alert(msg, AlertBox.Type.ERROR, 3, null);
        // var a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        // a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
    private boolean confirm(String msg) {
        var a = new Alert(Alert.AlertType.CONFIRMATION, msg, ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText(null); Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }
    private String errMsg(Throwable ex, String fb) { return ex!=null && ex.getMessage()!=null ? ex.getMessage() : fb; }

    private String requireNewPasswordOrAlert() {
        String pw = pfPassword.getText() == null ? "" : pfPassword.getText();
        String cf = pfConfirm.getText()  == null ? "" : pfConfirm.getText();
        if (pw.length() < 6) { alert("Password minimal 6 karakter."); return null; }
        if (!pw.equals(cf))  { alert("Konfirmasi password tidak sama."); return null; }
        return pw;
    }

    private String optionalPasswordOrAlert() {
        String pw = pfPassword.getText() == null ? "" : pfPassword.getText();
        String cf = pfConfirm.getText()  == null ? "" : pfConfirm.getText();
        if (pw.isBlank() && cf.isBlank()) return null;         // tidak mengubah password
        if (pw.length() < 6) { alert("Password minimal 6 karakter."); return null; }
        if (!pw.equals(cf))  { alert("Konfirmasi password tidak sama."); return null; }
        return pw;
    }

    // === Wrappers JSON ===
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserListResponse {
        private boolean status; private String message; private List<User> data;
        public boolean isStatus() { return status; } public String getMessage() { return message; }
        public List<User> getData() { return data; }
        public void setStatus(boolean status) { this.status = status; } public void setMessage(String m) { this.message = m; }
        public void setData(List<User> data) { this.data = data; }
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserResponse {
        private boolean status; private String message; private User data;
        public boolean isStatus() { return status; } public String getMessage() { return message; }
        public User getData() { return data; }
        public void setStatus(boolean status) { this.status = status; } public void setMessage(String m) { this.message = m; }
        public void setData(User data) { this.data = data; }
    }
}
