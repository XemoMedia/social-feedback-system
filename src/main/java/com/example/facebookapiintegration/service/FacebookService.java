package com.example.facebookapiintegration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * FacebookService: calls Graph API using TokenService for access token.
 * Real API is used by default; errors returned to caller.
 */
@Service
public class FacebookService {
    private static final Logger log = LoggerFactory.getLogger(FacebookService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private final TokenService tokenService;

    @Value("${facebook.api-version:v19.0}")
    private String apiVersion;

    @Value("${facebook.app-id:}")
    private String appId;

    @Value("${facebook.redirect-uri:http://localhost:8080/api/facebook/oauth/exchange}")
    private String redirectUri;

    public FacebookService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public String generateLoginUrl() {
        try {
            String encoded = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
            String scope = URLEncoder.encode("instagram_basic,instagram_manage_comments,pages_read_engagement,pages_manage_posts,pages_read_user_content,pages_show_list,public_profile", StandardCharsets.UTF_8);
            return String.format("https://www.facebook.com/%s/dialog/oauth?client_id=%s&redirect_uri=%s&scope=%s&response_type=code", apiVersion, appId, encoded, scope);
        } catch (Exception e) {
            log.error("Error generating login URL", e);
            return "ERROR_GENERATING_URL:" + e.getMessage();
        }
    }

    private JsonNode callGraph(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        int rc = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader((rc>=200 && rc<300)?conn.getInputStream():conn.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return mapper.readTree(sb.toString());
    }

    public List<Map<String,Object>> getUserPages() throws Exception {
        String token = tokenService.getValidAccessToken();
        String url = String.format("https://graph.facebook.com/%s/me/accounts?access_token=%s&fields=id,name,access_token", apiVersion, URLEncoder.encode(token, StandardCharsets.UTF_8));
        JsonNode resp = callGraph(url);
        // propagate errors from Graph if present
        if (resp.has("error")) throw new RuntimeException(resp.get("error").toString());
        List<Map<String,Object>> out = new ArrayList<>();
        for (JsonNode n : resp.path("data")) {
            Map<String,Object> m = new HashMap<>();
            m.put("id", n.path("id").asText());
            m.put("name", n.path("name").asText());
            out.add(m);
        }
        return out;
    }

    public List<Map<String,Object>> getPagePosts(String pageId) throws Exception {
        String token = tokenService.getValidAccessToken();
        String url = String.format("https://graph.facebook.com/%s/%s/posts?access_token=%s&fields=id,message,created_time,permalink_url&limit=25", apiVersion, URLEncoder.encode(pageId, StandardCharsets.UTF_8), URLEncoder.encode(token, StandardCharsets.UTF_8));
        JsonNode resp = callGraph(url);
        if (resp.has("error")) throw new RuntimeException(resp.get("error").toString());
        List<Map<String,Object>> out = new ArrayList<>();
        for (JsonNode n : resp.path("data")) {
            Map<String,Object> m = new HashMap<>();
            m.put("id", n.path("id").asText());
            m.put("message", n.path("message").asText(null));
            m.put("created_time", n.path("created_time").asText(null));
            m.put("permalink_url", n.path("permalink_url").asText(null));
            out.add(m);
        }
        return out;
    }

    public Map<String,Object> getPostDetails(String postId) throws Exception {
        String token = tokenService.getValidAccessToken();
        String fields = URLEncoder.encode("id,message,comments.limit(50){from,message,created_time},reactions.summary(true).limit(0)", StandardCharsets.UTF_8);
        String url = String.format("https://graph.facebook.com/%s/%s?access_token=%s&fields=%s", apiVersion, URLEncoder.encode(postId, StandardCharsets.UTF_8), URLEncoder.encode(token, StandardCharsets.UTF_8), fields);
        JsonNode resp = callGraph(url);
        if (resp.has("error")) throw new RuntimeException(resp.get("error").toString());
        Map<String,Object> result = new HashMap<>();
        result.put("id", resp.path("id").asText());
        result.put("message", resp.path("message").asText(null));
        int reactions = resp.path("reactions").path("summary").path("total_count").asInt(0);
        result.put("reactions_count", reactions);
        List<Map<String,Object>> comments = new ArrayList<>();
        for (JsonNode c : resp.path("comments").path("data")) {
            Map<String,Object> cm = new HashMap<>();
            cm.put("id", c.path("id").asText());
            cm.put("from", c.path("from").path("name").asText(null));
            cm.put("message", c.path("message").asText(null));
            cm.put("created_time", c.path("created_time").asText(null));
            comments.add(cm);
        }
        result.put("comments", comments);
        return result;
    }

    public Map<String,Object> getPostReactions(String postId) throws Exception {
        String token = tokenService.getValidAccessToken();
        String url = String.format("https://graph.facebook.com/%s/%s/reactions?access_token=%s&summary=true&limit=0", apiVersion, URLEncoder.encode(postId, StandardCharsets.UTF_8), URLEncoder.encode(token, StandardCharsets.UTF_8));
        JsonNode resp = callGraph(url);
        if (resp.has("error")) throw new RuntimeException(resp.get("error").toString());
        int total = resp.path("summary").path("total_count").asInt(0);
        return Map.of("postId", postId, "total_reactions", total);
    }
}
