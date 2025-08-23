package org.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Role {
    private Integer id;
    private String name;

    @JsonProperty("accesses")
    private List<String> roleAccesses;

    // === getters & setters ===
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<String> getRoleAccesses() { return roleAccesses; }
    public void setRoleAccesses(List<String> roleAccesses) { this.roleAccesses = roleAccesses; }

    // Helpers
    public int getAccessCount() { return roleAccesses == null ? 0 : roleAccesses.size(); }
    public String getAccessesJoined() {
        return roleAccesses == null || roleAccesses.isEmpty() ? "-" : String.join(", ", roleAccesses);
    }
}