package org.services;

import org.models.User;
import org.models.UserResponse;
import org.util.Http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class Session {
    private static String token = null;
    private static User user;

    private Session() {}

    public static void set(String t, String u) { 
        token = t;
        try {
            Http http = new Http();
            String json = http.GET("api/profile", java.util.Map.of());
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            UserResponse ur = mapper.readValue(json, UserResponse.class);
            if (!ur.isStatus()) throw new IllegalStateException(ur.getMessage());
            user = ur.getData();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static String token() { return token; }

    public static User user() { return user; }

    public static void clear() { 
        token = null; 
        user = null; 
    }
}
