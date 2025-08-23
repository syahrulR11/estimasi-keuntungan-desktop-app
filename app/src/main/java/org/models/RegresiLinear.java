package org.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

public class RegresiLinear {

    @JsonProperty("intercept")
    public double intercept;

    @JsonProperty("koefisien")
    public Map<String, Double> koefisien;

    @JsonProperty("pengaruh")
    public Map<String, Double> pengaruh;

    @JsonProperty("fitur_paling_berpengaruh")
    public String fiturPalingBerpengaruh;

    public String getFiturPalingBerpengaruhLabel() {
        return switch (fiturPalingBerpengaruh) {
            case "penjualan" -> "Penjualan";
            case "pembelian" -> "Pembelian";
            case "stok_barang" -> "Stok Barang";
            default -> fiturPalingBerpengaruh;
        };
    }

    public Map<String, Double> getKoefisienDenganLabel() {
        Map<String, Double> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : koefisien.entrySet()) {
            String label = switch (entry.getKey()) {
                case "penjualan" -> "Penjualan";
                case "pembelian" -> "Pembelian";
                case "stok_barang" -> "Stok Barang";
                default -> entry.getKey();
            };
            mapped.put(label, entry.getValue());
        }
        return mapped;
    }

    public Map<String, Double> getPengaruhDenganLabel() {
        Map<String, Double> mapped = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : pengaruh.entrySet()) {
            String label = switch (entry.getKey()) {
                case "penjualan" -> "Penjualan";
                case "pembelian" -> "Pembelian";
                case "stok_barang" -> "Stok Barang";
                default -> entry.getKey();
            };
            mapped.put(label, entry.getValue());
        }
        return mapped;
    }
}
