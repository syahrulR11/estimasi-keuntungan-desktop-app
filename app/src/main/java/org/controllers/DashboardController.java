package org.controllers;

import org.models.DataKeuangan;
import org.models.DataKeuanganResponse;
import org.models.Predict;
import org.models.PredictResponse;
import org.models.RegresiLinear;
import org.models.RegresiLinearResponse;
import org.util.Http;
import org.util.Util;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DashboardController {

    private PredictResponse predictResponse;
    private RegresiLinearResponse rlResponse;
    private DataKeuanganResponse keuanganResponse;

    private Predict predict;
    private RegresiLinear rl;
    private DataKeuangan[] keuangans;

    @FXML
    private Label
        labelInputTerakhir,
        totalPenjualanLabel,
        totalPembelianLabel,
        totalStokBarangLabel,
        totalNonOperasionalLabel,
        totalKeuntunganLabel,
        estimasiKeuntunganLabel,
        fiturDominanLabel;

    @FXML
    private LineChart<String, Number> lineChart;

    @FXML
    private BarChart<String, Number> barChart;

    @FXML
    public void initialize() {
        Http http = new Http();
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());

            String responsePredict = http.GET("api/model/predict", null);
            predictResponse = mapper.readValue(responsePredict, PredictResponse.class);
            if (!predictResponse.isStatus()) throw new Exception(predictResponse.getMessage());
            predict = predictResponse.getData();

            String responseRL = http.GET("api/model/koef", null);
            rlResponse = mapper.readValue(responseRL, RegresiLinearResponse.class);
            if (!rlResponse.isStatus()) throw new Exception(rlResponse.getMessage());
            rl = rlResponse.getData();

            Map<String, String> q = Map.of(
                "dashboard", "Ya"
            );
            String responseKeuangan = http.GET("api/keuangan", q);
            keuanganResponse = mapper.readValue(responseKeuangan, DataKeuanganResponse.class);
            if (!keuanganResponse.isStatus()) throw new Exception(keuanganResponse.getMessage());
            keuangans = keuanganResponse.getData();
            List<DataKeuangan> list = Arrays.asList(keuangans);
            Collections.reverse(list);
            keuangans = list.toArray(new DataKeuangan[0]);

        } catch (Exception e) {
            System.out.println(e);
        }

        labelInputTerakhir.setText("Input Data Terakhir: "+keuangans[keuangans.length - 1].getTanggalFormatIndo());
        totalPenjualanLabel.setText(Util.toRupiah(keuangans[keuangans.length - 1].getPenjualan()));
        totalPembelianLabel.setText(Util.toRupiah(keuangans[keuangans.length - 1].getPembelian()));
        totalStokBarangLabel.setText(Util.toRupiah(keuangans[keuangans.length - 1].getStokBarang()));
        totalKeuntunganLabel.setText(Util.toRupiah((keuangans[keuangans.length - 1].getKeuntungan())));
        estimasiKeuntunganLabel.setText(Util.toRupiah(predict.getFormatted()));
        fiturDominanLabel.setText(rl.getFiturPalingBerpengaruhLabel());

        // Tambahkan data ke LineChart
        XYChart.Series<String, Number> penjualanSeries = new XYChart.Series<>();
        penjualanSeries.setName("Penjualan");
        XYChart.Series<String, Number> pembelianSeries = new XYChart.Series<>();
        pembelianSeries.setName("Pembelian");
        XYChart.Series<String, Number> keuntunganSeries = new XYChart.Series<>();
        keuntunganSeries.setName("Keuntungan");
        for (DataKeuangan row : keuangans) {
            penjualanSeries.getData().add(new XYChart.Data<>(row.getTanggalFormatIndo(), row.getPenjualan()));
            pembelianSeries.getData().add(new XYChart.Data<>(row.getTanggalFormatIndo(), row.getPembelian()));
            keuntunganSeries.getData().add(new XYChart.Data<>(row.getTanggalFormatIndo(), row.getKeuntungan()));
        }
        for (XYChart.Data<String, Number> data : penjualanSeries.getData()) {
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Tooltip tooltip = new Tooltip(
                            data.getXValue() + ": " + Util.toRupiah(data.getYValue().doubleValue())
                    );
                    Tooltip.install(newNode, tooltip);
                }
            });
        }
        for (XYChart.Data<String, Number> data : pembelianSeries.getData()) {
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Tooltip tooltip = new Tooltip(
                            data.getXValue() + ": " + Util.toRupiah(data.getYValue().doubleValue())
                    );
                    Tooltip.install(newNode, tooltip);
                }
            });
        }
        for (XYChart.Data<String, Number> data : keuntunganSeries.getData()) {
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Tooltip tooltip = new Tooltip(
                            data.getXValue() + ": " + Util.toRupiah(data.getYValue().doubleValue())
                    );
                    Tooltip.install(newNode, tooltip);
                }
            });
        }
        lineChart.getData().addAll(penjualanSeries, pembelianSeries, keuntunganSeries);

        // Tambahkan data ke BarChart
        barChart.getData().clear();
        XYChart.Series<String, Number> fiturSeries = new XYChart.Series<>();
        fiturSeries.setName("Pengaruh Fitur");
        rl.getPengaruhDenganLabel().forEach((label, nilai) -> {
            fiturSeries.getData().add(new XYChart.Data<>(label, nilai));
        });
        for (XYChart.Data<String, Number> data : fiturSeries.getData()) {
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Tooltip tooltip = new Tooltip(
                        data.getXValue() + ": " + String.format("%.2f", data.getYValue().doubleValue())
                    );
                    Tooltip.install(newNode, tooltip);
                }
            });
        }
        barChart.getData().add(fiturSeries);
    }
}
