package org.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LaporanEstimasiResponse {
    private boolean status;
    private String message;
    private List<LaporanEstimasi> data;

    public boolean isStatus() { return status; }
    public String getMessage() { return message; }
    public List<LaporanEstimasi> getData() { return data; }

    public void setStatus(boolean s){ this.status = s; }
    public void setMessage(String m){ this.message = m; }
    public void setData(List<LaporanEstimasi> d){ this.data = d; }
}
