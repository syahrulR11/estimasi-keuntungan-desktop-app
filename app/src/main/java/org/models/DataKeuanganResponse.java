package org.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataKeuanganResponse {
    private boolean status;
    private String message;
    private DataKeuangan[] datas;

    public boolean isStatus() { return status; }
    public void setStatus(boolean status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public DataKeuangan[] getData() { return datas; }
    public void setData(DataKeuangan[] datas) { this.datas = datas; }
}
