package org.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PredictResponse {
    private boolean status;
    private String message;
    private Predict data;

    public boolean isStatus() { return status; }
    public void setStatus(boolean status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Predict getData() { return data; }
    public void setData(Predict data) { this.data = data; }
}
