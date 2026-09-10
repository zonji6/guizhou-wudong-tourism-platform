package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiEnvelope;
import com.guizhou.wudong.api.ApiRequestException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class V3IdentityController {
    private final V3IdentityService identityService;
    private final String publicOrigin;
    private final long userTtl;
    private final long adminTtl;
    private final long anonymousCarrierTtl;

    public V3IdentityController(
            V3IdentityService identityService,
            @Value("${wudong.public-web-origin:http://127.0.0.1:5174}") String publicOrigin,
            @Value("${wudong.auth.user-login-ttl-seconds:604800}") long userTtl,
            @Value("${wudong.auth.admin-login-ttl-seconds:28800}") long adminTtl,
            @Value("${wudong.anonymous.carrier-max-age-seconds:604800}") long anonymousCarrierTtl) {
        this.identityService = identityService;
        this.publicOrigin = publicOrigin;
        this.userTtl = userTtl;
        this.adminTtl = adminTtl;
        this.anonymousCarrierTtl = anonymousCarrierTtl;
    }

    @GetMapping("/api/auth/web/csrf")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> userCsrf(HttpServletRequest request) {
        return csrf(request, "USER", "WD_WEB_LANE", "WD_XSRF_WEB", "/api/auth/web", userTtl, false);
    }

    @GetMapping("/api/admin/auth/csrf")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> adminCsrf(HttpServletRequest request) {
        return csrf(request, "ADMIN", "WD_ADMIN_LANE", "WD_XSRF_ADMIN", "/api/admin/auth", adminTtl, false);
    }

    @GetMapping("/api/anonymous/web/csrf")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> anonymousCsrf(HttpServletRequest request) {
        return csrf(request, "ANONYMOUS", "WD_ANON_LANE", "WD_XSRF_ANON", "/", anonymousCarrierTtl, true);
    }

    @PostMapping("/api/auth/web/register")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> webRegister(@RequestBody Map<String, Object> body,
                                                                 HttpServletRequest request) {
        requireBrowserWrite(request, "WD_WEB_LANE", "WD_XSRF_WEB");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(identityService.register(body)));
    }

    @PostMapping("/api/auth/mini/register")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> miniRegister(@RequestBody Map<String, Object> body,
                                                                  HttpServletRequest request) {
        requireMiniOrigin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(identityService.register(body)));
    }

    @PostMapping("/api/auth/web/login")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> webLogin(@RequestBody Map<String, Object> body,
                                                              HttpServletRequest request) {
        V3Support.onlyKeys(body, "username", "password", "authRequestId", "expectedAuthGeneration");
        String lane = requireBrowserWrite(request, "WD_WEB_LANE", "WD_XSRF_WEB");
        V3IdentityService.LoginResult result = identityService.login("WEB", "USER", lane, body, false);
        return withLoginCookies(result, lane, "USER");
    }

    @PostMapping("/api/admin/auth/login")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> adminLogin(@RequestBody Map<String, Object> body,
                                                                HttpServletRequest request) {
        V3Support.onlyKeys(body, "username", "password", "authRequestId", "expectedAuthGeneration");
        String lane = requireBrowserWrite(request, "WD_ADMIN_LANE", "WD_XSRF_ADMIN");
        V3IdentityService.LoginResult result = identityService.login("WEB", "ADMIN", lane, body, false);
        return withLoginCookies(result, lane, "ADMIN");
    }

    @PostMapping("/api/auth/mini/login")
    ApiEnvelope<Map<String, Object>> miniLogin(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireMiniOrigin(request);
        V3Support.onlyKeys(body, "username", "password", "clientInstanceId", "authRequestId",
                "expectedAuthGeneration");
        String lane = V3Support.uuid(body, "clientInstanceId");
        V3IdentityService.LoginResult result = identityService.login("MINI", "USER", lane, body, true);
        LinkedHashMap<String, Object> data = new LinkedHashMap<>(result.data());
        data.put("refreshToken", result.refreshToken());
        return ApiEnvelope.ok(data);
    }

    @PostMapping("/api/auth/web/refresh")
    ApiEnvelope<Map<String, Object>> webRefresh(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        V3Support.onlyKeys(body, "authRequestId", "expectedAuthGeneration");
        String lane = requireBrowserWrite(request, "WD_WEB_LANE", "WD_XSRF_WEB");
        return ApiEnvelope.ok(identityService.refresh("WEB", "USER", lane,
                V3Support.uuid(body, "authRequestId"), V3Support.nonNegativeInt(body, "expectedAuthGeneration"),
                null, cookies(request)).data());
    }

    @PostMapping("/api/admin/auth/refresh")
    ApiEnvelope<Map<String, Object>> adminRefresh(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        V3Support.onlyKeys(body, "authRequestId", "expectedAuthGeneration");
        String lane = requireBrowserWrite(request, "WD_ADMIN_LANE", "WD_XSRF_ADMIN");
        return ApiEnvelope.ok(identityService.refresh("WEB", "ADMIN", lane,
                V3Support.uuid(body, "authRequestId"), V3Support.nonNegativeInt(body, "expectedAuthGeneration"),
                null, cookies(request)).data());
    }

    @PostMapping("/api/auth/mini/refresh")
    ApiEnvelope<Map<String, Object>> miniRefresh(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireMiniOrigin(request);
        V3Support.onlyKeys(body, "clientInstanceId", "authRequestId", "expectedAuthGeneration", "refreshToken");
        return ApiEnvelope.ok(identityService.refresh("MINI", "USER", V3Support.uuid(body, "clientInstanceId"),
                V3Support.uuid(body, "authRequestId"), V3Support.nonNegativeInt(body, "expectedAuthGeneration"),
                V3Support.rawRequiredText(body, "refreshToken"), null).data());
    }

    @PostMapping("/api/auth/web/logout")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> webLogout(@RequestBody Map<String, Object> body,
                                                               HttpServletRequest request) {
        V3Support.onlyKeys(body, "authRequestId", "expectedAuthGeneration");
        String lane = requireBrowserWrite(request, "WD_WEB_LANE", "WD_XSRF_WEB");
        V3Support.V3Principal principal = V3Support.principal("USER");
        Map<String, Object> result = identityService.logout(principal, "WEB", lane,
                V3Support.uuid(body, "authRequestId"), V3Support.nonNegativeInt(body, "expectedAuthGeneration"));
        return deleteRefreshCookie(result, principal, "USER");
    }

    @PostMapping("/api/admin/auth/logout")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> adminLogout(@RequestBody Map<String, Object> body,
                                                                 HttpServletRequest request) {
        V3Support.onlyKeys(body, "authRequestId", "expectedAuthGeneration");
        String lane = requireBrowserWrite(request, "WD_ADMIN_LANE", "WD_XSRF_ADMIN");
        V3Support.V3Principal principal = V3Support.principal("ADMIN");
        Map<String, Object> result = identityService.logout(principal, "WEB", lane,
                V3Support.uuid(body, "authRequestId"), V3Support.nonNegativeInt(body, "expectedAuthGeneration"));
        return deleteRefreshCookie(result, principal, "ADMIN");
    }

    @PostMapping("/api/auth/mini/logout")
    ApiEnvelope<Map<String, Object>> miniLogout(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireMiniOrigin(request);
        V3Support.onlyKeys(body, "clientInstanceId", "authRequestId", "expectedAuthGeneration");
        V3Support.V3Principal principal = V3Support.principal("USER");
        return ApiEnvelope.ok(identityService.logout(principal, "MINI", V3Support.uuid(body, "clientInstanceId"),
                V3Support.uuid(body, "authRequestId"), V3Support.nonNegativeInt(body, "expectedAuthGeneration")));
    }

    @GetMapping("/api/me/profile")
    ApiEnvelope<Map<String, Object>> userProfile() {
        return ApiEnvelope.ok(identityService.accountView(V3Support.principal("USER")));
    }

    @GetMapping("/api/admin/auth/profile")
    ApiEnvelope<Map<String, Object>> adminProfile() {
        return ApiEnvelope.ok(identityService.accountView(V3Support.principal("ADMIN")));
    }

    @PostMapping("/api/anonymous/web/sessions")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> anonymousWeb(@RequestBody Map<String, Object> body,
                                                                  HttpServletRequest request) {
        V3Support.onlyKeys(body, "anonymousRequestId", "expectedAnonymousGeneration");
        String lane = requireBrowserWrite(request, "WD_ANON_LANE", "WD_XSRF_ANON");
        V3IdentityService.AnonymousResult result = identityService.createAnonymous("WEB", lane,
                V3Support.uuid(body, "anonymousRequestId"),
                V3Support.nonNegativeInt(body, "expectedAnonymousGeneration"));
        return withAnonymousCookies(result, lane, false);
    }

    @PostMapping("/api/anonymous/mini/sessions")
    ApiEnvelope<Map<String, Object>> anonymousMini(@RequestBody Map<String, Object> body,
                                                   HttpServletRequest request) {
        requireMiniOrigin(request);
        V3Support.onlyKeys(body, "clientInstanceId", "anonymousRequestId", "expectedAnonymousGeneration");
        V3IdentityService.AnonymousResult result = identityService.createAnonymous("MINI",
                V3Support.uuid(body, "clientInstanceId"), V3Support.uuid(body, "anonymousRequestId"),
                V3Support.nonNegativeInt(body, "expectedAnonymousGeneration"));
        return ApiEnvelope.ok(anonymousView(result, true));
    }

    @PostMapping("/api/anonymous/web/cookie-sync")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> anonymousSync(@RequestBody Map<String, Object> body,
                                                                   HttpServletRequest request) {
        V3Support.onlyKeys(body, "anonymousRequestId", "expectedAnonymousGeneration");
        String lane = requireBrowserWrite(request, "WD_ANON_LANE", "WD_XSRF_ANON");
        V3IdentityService.AnonymousResult result = identityService.syncAnonymous(lane,
                V3Support.uuid(body, "anonymousRequestId"),
                V3Support.nonNegativeInt(body, "expectedAnonymousGeneration"), cookies(request));
        return withAnonymousCookies(result, lane, false);
    }

    private ResponseEntity<ApiEnvelope<Map<String, Object>>> csrf(HttpServletRequest request, String purpose,
                                                                  String laneCookie, String csrfCookie,
                                                                  String lanePath, long maxAge, boolean anonymous) {
        requireOrigin(request);
        String current = cookie(request, laneCookie);
        V3IdentityService.LaneBootstrap lane = identityService.bootstrap("WEB", purpose, current);
        String token = V3Support.opaqueToken();
        Map<String, Object> data = anonymous
                ? V3Support.map("csrfToken", token, "anonymousGeneration", lane.generation())
                : V3Support.map("csrfToken", token, "authGeneration", lane.generation());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookieValue(laneCookie, lane.laneId(), lanePath, true, maxAge));
        headers.add(HttpHeaders.SET_COOKIE, sessionCookieValue(csrfCookie, token, "/", false));
        return ResponseEntity.ok().headers(headers).body(ApiEnvelope.ok(data));
    }

    private ResponseEntity<ApiEnvelope<Map<String, Object>>> withLoginCookies(V3IdentityService.LoginResult result,
                                                                               String lane, String purpose) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), result.loginExpiresAt()).toSeconds());
        String laneName = "ADMIN".equals(purpose) ? "WD_ADMIN_LANE" : "WD_WEB_LANE";
        String path = "ADMIN".equals(purpose) ? "/api/admin/auth" : "/api/auth/web";
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookieValue(laneName, lane, path, true, maxAge));
        headers.add(HttpHeaders.SET_COOKIE, cookieValue(V3IdentityService.refreshCookieName(purpose, result.sessionId()),
                result.refreshToken(), path, true, maxAge));
        return ResponseEntity.ok().headers(headers).body(ApiEnvelope.ok(result.data()));
    }

    private ResponseEntity<ApiEnvelope<Map<String, Object>>> deleteRefreshCookie(Map<String, Object> result,
                                                                                  V3Support.V3Principal principal,
                                                                                  String purpose) {
        String path = "ADMIN".equals(purpose) ? "/api/admin/auth" : "/api/auth/web";
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookieValue(V3IdentityService.refreshCookieName(purpose,
                principal.sessionId()), "", path, true, 0));
        return ResponseEntity.ok().headers(headers).body(ApiEnvelope.ok(result));
    }

    private ResponseEntity<ApiEnvelope<Map<String, Object>>> withAnonymousCookies(
            V3IdentityService.AnonymousResult result, String lane, boolean includeCredential) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookieValue("WD_ANON_LANE", lane, "/", true, anonymousCarrierTtl));
        headers.add(HttpHeaders.SET_COOKIE, cookieValue(V3IdentityService.anonymousCookieName(result.threadId()),
                result.credential(), "/", true, anonymousCarrierTtl));
        return ResponseEntity.ok().headers(headers).body(ApiEnvelope.ok(anonymousView(result, includeCredential)));
    }

    private Map<String, Object> anonymousView(V3IdentityService.AnonymousResult result, boolean includeCredential) {
        LinkedHashMap<String, Object> data = V3Support.map("anonymousRequestId", result.requestId(),
                "threadId", result.threadId(), "mode", "ANONYMOUS", "anonymousGeneration", result.generation(),
                "sessionExpiresAt", result.sessionExpiresAt().toString(),
                "carrierExpiresAt", result.carrierExpiresAt().toString());
        if (includeCredential) {
            data.put("anonymousCredential", result.credential());
        }
        return data;
    }

    private String requireBrowserWrite(HttpServletRequest request, String laneCookie, String csrfCookie) {
        requireOrigin(request);
        String lane = cookie(request, laneCookie);
        String csrf = cookie(request, csrfCookie);
        String header = request.getHeader("X-Wudong-CSRF");
        if (lane == null || csrf == null || header == null || !constantEquals(csrf, header)) {
            throw new ApiRequestException(HttpStatus.FORBIDDEN, "CSRF_REJECTED", "浏览器请求校验失败");
        }
        return V3Support.uuid(lane, "laneId");
    }

    private void requireOrigin(HttpServletRequest request) {
        if (!publicOrigin.equals(request.getHeader(HttpHeaders.ORIGIN))) {
            throw new ApiRequestException(HttpStatus.FORBIDDEN, "ORIGIN_REJECTED", "浏览器来源不受信任");
        }
    }

    private static void requireMiniOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !"https://servicewechat.com".equals(origin)) {
            throw new ApiRequestException(HttpStatus.FORBIDDEN, "ORIGIN_REJECTED", "小程序来源不受信任");
        }
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies()).filter(value -> name.equals(value.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private static Map<String, String> cookies(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Map.of();
        }
        return Arrays.stream(request.getCookies()).collect(Collectors.toMap(Cookie::getName, Cookie::getValue,
                (left, right) -> right));
    }

    private static boolean constantEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static String cookieValue(String name, String value, String path, boolean httpOnly, long maxAge) {
        return ResponseCookie.from(name, value).path(path).httpOnly(httpOnly).secure(false).sameSite("Lax")
                .maxAge(Duration.ofSeconds(maxAge)).build().toString();
    }

    private static String sessionCookieValue(String name, String value, String path, boolean httpOnly) {
        return ResponseCookie.from(name, value).path(path).httpOnly(httpOnly).secure(false).sameSite("Lax")
                .build().toString();
    }
}
