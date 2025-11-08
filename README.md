# Facebook API Integration (Dynamic Token) — Final (Real-API Default)

This build enables **real Facebook Graph API calls by default**. If no valid token is present, endpoints will return a clear error instructing you to authorize via `/api/facebook/login-url`.

## Setup
1. Export environment variables:
```bash
export FACEBOOK_APP_ID=your_app_id
export FACEBOOK_APP_SECRET=your_app_secret
export FACEBOOK_REDIRECT_URI=http://localhost:8080/api/facebook/oauth/exchange
```

2. Build & run:
```bash
mvn clean package
mvn spring-boot:run
```

3. Endpoints (use Postman collection included):
- GET `/api/facebook/login-url` — open returned URL to authorize and get `code`.
- GET `/api/facebook/oauth/exchange?code=THE_CODE` — exchanges code for a long-lived token and saves it locally.
- GET `/api/facebook/pages` — lists user-managed pages (real data if token valid).
- GET `/api/facebook/pages/{pageId}/posts` — lists recent posts for the page.
- GET `/api/facebook/posts/{postId}/details` — comments + reactions for the post.
- GET `/api/facebook/token/status` — token diagnostics.

Notes:
- This build prefers real API responses. If a Graph API call fails (network, permissions), you get a 500 with the Graph error message.
- Token is stored in `src/main/resources/token.json`.
