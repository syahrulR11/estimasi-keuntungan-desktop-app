package org.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Predict {

    @JsonProperty("predict")
    public String prediksiKeuntunganBesok;

    public double getFormatted() {
        return Double.parseDouble(prediksiKeuntunganBesok);
    }
}
