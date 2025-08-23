package org.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RegresiLinearResponse {
    private boolean status;
    private String message;
    private RegresiLinear data;

    public boolean isStatus() { return status; }
    public void setStatus(boolean status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public RegresiLinear getData() { return data; }
    public void setData(RegresiLinear data) { this.data = data; }
}
