package org.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RoleResponse {
    private boolean status;
    private String message;
    private List<Role> data;
    private Integer total; // kalau API mengirim total

    public boolean isStatus() { return status; }
    public void setStatus(boolean status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<Role> getData() { return data; }
    public void setData(List<Role> data) { this.data = data; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
}