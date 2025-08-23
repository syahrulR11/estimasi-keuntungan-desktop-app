package org.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LaporanEstimasi {
    private Integer id;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate tanggal;

    private double intercept;

    @JsonProperty("koef_penjualan")    private double koefPenjualan;
    @JsonProperty("koef_pembelian")    private double koefPembelian;
    @JsonProperty("koef_stok_barang")  private double koefStokBarang;

    @JsonProperty("pengaruh_penjualan")   private double pengaruhPenjualan;
    @JsonProperty("pengaruh_pembelian")   private double pengaruhPembelian;
    @JsonProperty("pengaruh_stok_barang") private double pengaruhStokBarang;

    @JsonProperty("actual_keuntungan")   private double actualKeuntungan;
    @JsonProperty("estimasi_keuntungan") private double estimasiKeuntungan;

    @JsonProperty("r2_score") private double r2Score;

    // ===== getters =====
    public Integer getId() { return id; }
    public LocalDate getTanggal() { return tanggal; }
    public double getIntercept() { return intercept; }
    public double getKoefPenjualan() { return koefPenjualan; }
    public double getKoefPembelian() { return koefPembelian; }
    public double getKoefStokBarang() { return koefStokBarang; }
    public double getPengaruhPenjualan() { return pengaruhPenjualan; }
    public double getPengaruhPembelian() { return pengaruhPembelian; }
    public double getPengaruhStokBarang() { return pengaruhStokBarang; }
    public double getActualKeuntungan() { return actualKeuntungan; }
    public double getEstimasiKeuntungan() { return estimasiKeuntungan; }
    public double getR2Score() { return r2Score; }

    // helper label tanggal indo
    public String getTanggalIndo() {
        if (tanggal == null) return "";
        return tanggal.format(DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("id","ID")));
    }
}
