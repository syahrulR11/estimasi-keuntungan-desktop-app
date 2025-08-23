package org.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserResponse {
    private boolean status;
    private String message;
    private User data;

    public boolean isStatus() { return status; }
    public void setStatus(boolean status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public User getData() { return data; }
    public void setData(User data) { this.data = data; }
}
