package org.util;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import org.services.Session;

public class Http {

    private final HttpClient client;
    private final String host = "http://localhost:8080/";

    public Http() {
        this.client = HttpClient.newHttpClient();
    }

    private String buildUrl(String path, Map<String,String> q) {
        // path boleh relatif: "api/model/preprocessing/image" ATAU absolut: "http://..."
        String base = path.startsWith("http") ? path
                : (host.endsWith("/") ? host : host + "/") +
                  (path.startsWith("/") ? path.substring(1) : path);
        if (q == null || q.isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base);
        sb.append(base.contains("?") ? "&" : "?");
        boolean first = true;
        for (var e : q.entrySet()) {
            if (!first) sb.append("&"); first = false;
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
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

    public byte[] GET_BYTES(String path, Map<String,String> q) throws Exception {
        String url = buildUrl(path, q);
        String t = Session.token();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .GET().timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + t).build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200)
            throw new IllegalStateException("HTTP " + resp.statusCode() + " untuk " + url);
        String ctype = resp.headers().firstValue("Content-Type").orElse("");
        if (!ctype.startsWith("image/")) {
            String head = new String(resp.body(), StandardCharsets.UTF_8);
            if (head.length() > 120) head = head.substring(0,120) + "...";
            throw new IllegalStateException("Respons bukan gambar (Content-Type=" + ctype + "): " + head);
        }
        return resp.body();
    }
}
