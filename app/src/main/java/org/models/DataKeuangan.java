package org.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataKeuangan {

    @JsonProperty("tanggal")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate tanggal;

    @JsonProperty("penjualan")
    private double penjualan;

    @JsonProperty("pembelian")
    private double pembelian;

    @JsonProperty("stok_barang")
    private double stokBarang;

    @JsonProperty("keuntungan")
    private double keuntungan;

    @JsonProperty("created_by")
    private Integer createdBy;   // id user pembuat

    @JsonProperty("creator")
    private Creator creator;     // objek user pembuat

    // ===== getters =====
    public LocalDate getTanggal() { return tanggal; }
    public double getPenjualan()  { return penjualan; }
    public double getPembelian()  { return pembelian; }
    public double getStokBarang() { return stokBarang; }
    public double getKeuntungan() { return keuntungan; }
    public Integer getCreatedBy() { return createdBy; }
    public Creator getCreator()   { return creator; }

    // Format tanggal Indonesia
    public String getTanggalFormatIndo() {
        if (tanggal == null) return "";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", new Locale("id","ID"));
        return tanggal.format(fmt);
    }

    // Helper akses cepat
    public String getCreatorName() { return creator != null ? creator.getName() : null; }

    // ===== nested model untuk "creator" =====
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Creator {
        private Integer id;
        private String name;
        private String email;
        private String username;
        @JsonProperty("role_name")
        private String roleName;

        public Integer getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getUsername() { return username; }
        public String getRoleName() { return roleName; }
    }
}
