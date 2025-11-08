package com.example.facebookapiintegration.controller;

import com.example.facebookapiintegration.service.FacebookService;
import com.example.facebookapiintegration.service.TokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/facebook")
public class FacebookController {

    private final FacebookService facebookService;
    private final TokenService tokenService;

    public FacebookController(FacebookService facebookService, TokenService tokenService) {
        this.facebookService = facebookService;
        this.tokenService = tokenService;
    }

    @GetMapping("/login-url")
    public ResponseEntity<Map<String, String>> loginUrl() {
        return ResponseEntity.ok(Map.of("login_url", facebookService.generateLoginUrl()));
    }

    @GetMapping("/oauth/exchange")
    public ResponseEntity<?> exchange(@RequestParam(required = true) String code) {
        try {
            Map<String,Object> token = tokenService.exchangeCodeForLongLivedToken(code);
            return ResponseEntity.ok(token);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pages")
    public ResponseEntity<?> pages() {
        try {
            List<Map<String,Object>> pages = facebookService.getUserPages();
            return ResponseEntity.ok(pages);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/pages/{pageId}/posts")
    public ResponseEntity<?> posts(@PathVariable String pageId) {
        try {
            List<Map<String,Object>> posts = facebookService.getPagePosts(pageId);
            return ResponseEntity.ok(posts);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/posts/{postId}/details")
    public ResponseEntity<?> postDetails(@PathVariable String postId) {
        try {
            Map<String,Object> details = facebookService.getPostDetails(postId);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/posts/{postId}/reactions")
    public ResponseEntity<?> postReactions(@PathVariable String postId) {
        try {
            Map<String,Object> reactions = facebookService.getPostReactions(postId);
            return ResponseEntity.ok(reactions);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/token/status")
    public ResponseEntity<?> tokenStatus() {
        try {
            Map<String,Object> status = tokenService.getTokenStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
