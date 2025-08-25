package org.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PreProcessing {

    @JsonProperty("tanggal")
    public String tanggal;                 // simpan sebagai String agar fleksibel format

    @JsonProperty("penjualan")
    public Double penjualan;

    @JsonProperty("pembelian")
    public Double pembelian;

    @JsonProperty("stok_barang")
    public Double stokBarang;

    @JsonProperty("keuntungan")
    public Double keuntungan;

    @JsonProperty("keuntungan_smoothed")
    public Double keuntunganSmoothed;      // opsional, ada jika smoothing diaktifkan

    // Helper agar aman dipakai di UI tanpa NPE
    public double getPenjualanSafe()        { return penjualan == null ? Double.NaN : penjualan; }
    public double getPembelianSafe()        { return pembelian == null ? Double.NaN : pembelian; }
    public double getStokBarangSafe()       { return stokBarang == null ? Double.NaN : stokBarang; }
    public double getKeuntunganSafe()       { return keuntungan == null ? Double.NaN : keuntungan; }
    public double getKeuntunganSmoothedSafe(){ return keuntunganSmoothed == null ? Double.NaN : keuntunganSmoothed; }
}
