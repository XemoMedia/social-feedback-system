package com.example.facebookapiintegration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * TokenService handles token persistence, exchange and refresh.
 * This variant will require that a token exist for API calls; if missing you must authorize.
 */
@Service
public class TokenService {
    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${token.file:src/main/resources/token.json}")
    private String tokenFile;

    @Value("${facebook.api-version:v19.0}")
    private String apiVersion;

    @Value("${facebook.app-id:}")
    private String appId;

    //@Value("${facebook.app-secret:}")
    private String appSecret="3c6c249d40a06484a9b8d6db9302ca4d";

    @Value("${facebook.redirect-uri:http://localhost:8080/api/facebook/oauth/exchange}")
    private String redirectUri;

    public String getAppId() { return appId; }
    public String getAppSecret() { return appSecret; }
    public String getRedirectUri() { return redirectUri; }

    private JsonNode callUrl(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        int rc = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                (rc >= 200 && rc < 300) ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8
        ));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return objectMapper.readTree(sb.toString());
    }

    public Map<String,Object> exchangeCodeForLongLivedToken(String code) throws Exception {
        // Exchange code for short-lived
        String shortUrl = String.format("https://graph.facebook.com/%s/oauth/access_token?client_id=%s&redirect_uri=%s&client_secret=%s&code=%s",
                apiVersion, appId, java.net.URLEncoder.encode(redirectUri, StandardCharsets.UTF_8), appSecret, java.net.URLEncoder.encode(code, StandardCharsets.UTF_8));
        JsonNode shortResp = callUrl(shortUrl);
        if (!shortResp.has("access_token")) {
            throw new RuntimeException("Failed to obtain short-lived token: " + shortResp.toString());
        }
        String shortToken = shortResp.get("access_token").asText();

        // Exchange for long-lived
        String exch = String.format("https://graph.facebook.com/%s/oauth/access_token?grant_type=fb_exchange_token&client_id=%s&client_secret=%s&fb_exchange_token=%s",
                apiVersion, appId, appSecret, java.net.URLEncoder.encode(shortToken, StandardCharsets.UTF_8));
        JsonNode longResp = callUrl(exch);

        // add issued_at
        ((com.fasterxml.jackson.databind.node.ObjectNode) longResp).put("issued_at", Instant.now().getEpochSecond());
        saveTokenJson(longResp);
        Map<String,Object> out = objectMapper.convertValue(longResp, Map.class);
        log.info("Saved long-lived token (expires_in={}): {}", longResp.path("expires_in").asLong(), out.keySet());
        return out;
    }

    public synchronized void saveTokenJson(JsonNode tokenJson) {
        try {
            File f = new File(tokenFile);
            f.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(f, tokenJson);
            log.info("Token saved to {}", f.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to save token.json", e);
        }
    }

    public synchronized Map<String,Object> loadToken() {
        try {
            File f = new File(tokenFile);
            if (!f.exists()) return null;
            JsonNode n = objectMapper.readTree(f);
            return objectMapper.convertValue(n, Map.class);
        } catch (Exception e) {
            log.error("Failed to read token.json", e);
            return null;
        }
    }

    public synchronized String getValidAccessToken() throws Exception {
        Map<String,Object> token = loadToken();
        if (token == null || !token.containsKey("access_token")) {
            throw new IllegalStateException("No token.json found. Authorize via /api/facebook/login-url and exchange the code.");
        }
        // check expiry (expires_in is seconds), compute remaining using issued_at
        long expiresIn = token.getOrDefault("expires_in", 0) instanceof Number ? ((Number)token.get("expires_in")).longValue() : 0L;
        long issuedAt = token.getOrDefault("issued_at", Instant.now().getEpochSecond()) instanceof Number ? ((Number)token.get("issued_at")).longValue() : Instant.now().getEpochSecond();
        long now = Instant.now().getEpochSecond();
        long elapsed = now - issuedAt;
        long remaining = expiresIn - elapsed;
        // if less than 7 days remaining, refresh
        if (remaining < 7L*24*3600 && token.containsKey("access_token")) {
            log.info("Token expiring soon ({}s) — refreshing...", remaining);
            try {
                String refreshUrl = String.format("https://graph.facebook.com/%s/oauth/access_token?grant_type=fb_exchange_token&client_id=%s&client_secret=%s&fb_exchange_token=%s",
                        apiVersion, appId, appSecret, java.net.URLEncoder.encode((String)token.get("access_token"), StandardCharsets.UTF_8));
                JsonNode refreshed = callUrl(refreshUrl);
                ((com.fasterxml.jackson.databind.node.ObjectNode) refreshed).put("issued_at", Instant.now().getEpochSecond());
                saveTokenJson(refreshed);
                Map<String,Object> out = objectMapper.convertValue(refreshed, Map.class);
                return (String) out.get("access_token");
            } catch (Exception e) {
                log.error("Failed to refresh token, falling back to existing token", e);
                return (String) token.get("access_token");
            }
        }
        return (String) token.get("access_token");
    }

    public Map<String,Object> getTokenStatus() {
        Map<String,Object> status = new HashMap<>();
        Map<String,Object> token = loadToken();
        if (token == null) {
            status.put("status", "NO_TOKEN");
            status.put("message", "Visit /api/facebook/login-url to authorize");
            return status;
        }
        long expiresIn = token.getOrDefault("expires_in", 0) instanceof Number ? ((Number)token.get("expires_in")).longValue() : 0L;
        long issuedAt = token.getOrDefault("issued_at", Instant.now().getEpochSecond()) instanceof Number ? ((Number)token.get("issued_at")).longValue() : Instant.now().getEpochSecond();
        long now = Instant.now().getEpochSecond();
        long remaining = expiresIn - (now - issuedAt);
        status.put("access_token_length", ((String)token.get("access_token")).length());
        status.put("expires_in_seconds", remaining);
        status.put("last_refreshed", token.getOrDefault("issued_at", "unknown"));
        status.put("status", remaining > 0 ? "OK" : "EXPIRED");
        return status;
    }
}
