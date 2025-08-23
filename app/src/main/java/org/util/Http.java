package org.util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import org.services.Session;

public class Http {

    private final HttpClient client;
    private final String host = "http://localhost:8080/";

    public Http() {
        this.client = HttpClient.newHttpClient();
    }

    public String GET(String url, Map<String, String> queryParams) throws Exception {
        String fullUrl = host + url;
        if (queryParams != null && !queryParams.isEmpty()) {
            fullUrl += buildQueryString(queryParams);
        }
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(new URI(fullUrl))
                .GET();
        String t = Session.token();
        if (t != null) b.header("Authorization", "Bearer " + t);
        HttpResponse<String> response = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String POST(String url, String jsonBody) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(new URI(host + url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        String t = Session.token();
        if (t != null) b.header("Authorization", "Bearer " + t);
        HttpResponse<String> response = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String PUT(String url, String jsonBody) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(new URI(host + url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody));
        String t = Session.token();
        if (t != null) b.header("Authorization", "Bearer " + t);
        HttpResponse<String> response = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String DELETE(String url) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(new URI(host + url))
                .header("Content-Type", "application/json")
                .DELETE();
        String t = Session.token();
        if (t != null) b.header("Authorization", "Bearer " + t);
        HttpResponse<String> response = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String buildQueryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&", "?", "");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            joiner.add(
                URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" +
                URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
            );
        }
        return joiner.toString();
    }
}
