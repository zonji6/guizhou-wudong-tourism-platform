package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class V3IdentityService {
    private static final long TERMINAL_GRACE_SECONDS = 86400;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final V3JwtService jwtService;
    private final long userTtl;
    private final long adminTtl;
    private final long anonymousTtl;
    private final long anonymousCarrierTtl;

    public V3IdentityService(
            JdbcTemplate jdbc,
            StringRedisTemplate redis,
            PasswordEncoder passwordEncoder,
            V3JwtService jwtService,
            @Value("${wudong.auth.user-login-ttl-seconds:604800}") long userTtl,
            @Value("${wudong.auth.admin-login-ttl-seconds:28800}") long adminTtl,
            @Value("${wudong.anonymous.idle-ttl-seconds:86400}") long anonymousTtl,
            @Value("${wudong.anonymous.carrier-max-age-seconds:604800}") long anonymousCarrierTtl) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userTtl = userTtl;
        this.adminTtl = adminTtl;
        this.anonymousTtl = anonymousTtl;
        this.anonymousCarrierTtl = anonymousCarrierTtl;
    }

    public Map<String, Object> register(Map<String, Object> body) {
        V3Support.onlyKeys(body, "username", "password", "nickname");
        String username = V3Support.username(body);
        String password = V3Support.password(body);
        String nickname = body.containsKey("nickname") && body.get("nickname") != null
                ? V3Support.requiredText(body, "nickname", 1, 40) : username;
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO platform_account(id, username, password_hash, nickname, role) VALUES (?, ?, ?, ?, 'USER')",
                    id, username, passwordEncoder.encode(password), nickname);
        } catch (DuplicateKeyException exception) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "用户名已存在");
        }
        return V3Support.map("id", id, "username", username, "nickname", nickname, "role", "USER",
                "nextAction", "LOGIN");
    }

    public LaneBootstrap bootstrap(String surface, String purpose, String laneId) {
        try {
            String id = laneId == null ? UUID.randomUUID().toString() : V3Support.uuid(laneId, "laneId");
            String key = "ANONYMOUS".equals(purpose)
                    ? anonymousLaneKey(surface, id) : laneKey(surface, purpose, id);
            synchronized (this) {
                Map<String, String> lane = hashEntries(key);
                if (lane.isEmpty()) {
                    hashPut(key, V3Support.map("generation", "0", "currentSid", ""), anonymousCarrierTtl);
                    return new LaneBootstrap(id, 0);
                }
                return new LaneBootstrap(id, parseGeneration(lane));
            }
        } catch (DataAccessException exception) {
            throw authUnavailable();
        }
    }

    public synchronized LoginResult login(String surface, String purpose, String laneId,
                                           Map<String, Object> body, boolean mini) {
        String username = V3Support.username(body);
        String password = V3Support.password(body);
        String authRequestId = V3Support.uuid(body, "authRequestId");
        int expectedGeneration = V3Support.nonNegativeInt(body, "expectedAuthGeneration");
        String role = purpose;
        Map<String, Object> account = accountByUsername(username);
        if (account == null || !role.equals(account.get("role"))
                || !passwordEncoder.matches(password, account.get("password_hash").toString())) {
            throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }
        String laneKey = laneKey(surface, purpose, laneId);
        try {
            Map<String, String> lane = hashEntries(laneKey);
            int currentGeneration = lane.isEmpty() ? 0 : parseGeneration(lane);
            if ((!mini && lane.isEmpty()) || currentGeneration != expectedGeneration) {
                throw generationConflict(currentGeneration);
            }
            String previousSid = lane.get("currentSid");
            if (previousSid != null && !previousSid.isBlank()) {
                revoke(previousSid, "REVOKED");
            }
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant absoluteExpiresAt = now.plusSeconds("ADMIN".equals(purpose) ? adminTtl : userTtl);
            String sessionId = UUID.randomUUID().toString();
            String refreshToken = V3Support.opaqueToken();
            String refreshDigest = V3Support.sha256Ascii(refreshToken);
            int generation = currentGeneration + 1;
            long physicalTtl = Math.max(1, Duration.between(now, absoluteExpiresAt).toSeconds() + TERMINAL_GRACE_SECONDS);
            hashPut(loginKey(sessionId), V3Support.map(
                    "accountId", account.get("id").toString(), "surface", surface, "purpose", purpose,
                    "role", role, "laneId", laneId, "issuedAt", now.toString(),
                    "absoluteExpiresAt", absoluteExpiresAt.toString(), "refreshDigest", refreshDigest,
                    "state", "ACTIVE"), physicalTtl);
            redis.opsForValue().set(refreshKey(purpose, refreshDigest), sessionId, physicalTtl, TimeUnit.SECONDS);
            hashPut(laneKey, V3Support.map("generation", Integer.toString(generation), "currentSid", sessionId),
                    physicalTtl);
            return loginResult(account, authRequestId, generation, sessionId, purpose, refreshToken,
                    absoluteExpiresAt, false);
        } catch (DataAccessException exception) {
            throw authUnavailable();
        }
    }

    public synchronized LoginResult refresh(String surface, String purpose, String laneId,
                                             String authRequestId, int expectedGeneration, String refreshToken,
                                             Map<String, String> cookies) {
        V3Support.uuid(authRequestId, "authRequestId");
        try {
            Map<String, String> lane = hashEntries(laneKey(surface, purpose, laneId));
            int generation = lane.isEmpty() ? 0 : parseGeneration(lane);
            if (lane.isEmpty() || generation != expectedGeneration) {
                throw generationConflict(generation);
            }
            String sessionId = lane.get("currentSid");
            Map<String, String> login = liveLogin(sessionId);
            if (refreshToken == null && cookies != null) {
                refreshToken = cookies.get(refreshCookieName(purpose, sessionId));
            }
            String actualDigest = V3Support.sha256Ascii(refreshToken == null ? "" : refreshToken);
            if (!surface.equals(login.get("surface")) || !purpose.equals(login.get("purpose"))
                    || !laneId.equals(login.get("laneId"))
                    || !constantEquals(login.get("refreshDigest"), actualDigest)) {
                throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "刷新凭据无效");
            }
            Map<String, Object> account = accountById(login.get("accountId"));
            return loginResult(account, authRequestId, generation, sessionId, purpose, null,
                    Instant.parse(login.get("absoluteExpiresAt")), true);
        } catch (DataAccessException exception) {
            throw authUnavailable();
        }
    }

    public synchronized Map<String, Object> logout(V3Support.V3Principal principal, String surface, String laneId,
                                                    String authRequestId, int expectedGeneration) {
        V3Support.uuid(authRequestId, "authRequestId");
        try {
            String key = laneKey(surface, principal.purpose(), laneId);
            Map<String, String> lane = hashEntries(key);
            int generation = lane.isEmpty() ? 0 : parseGeneration(lane);
            if (lane.isEmpty() || generation != expectedGeneration || !principal.sessionId().equals(lane.get("currentSid"))) {
                throw generationConflict(generation);
            }
            String revokedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
            revoke(principal.sessionId(), "REVOKED");
            int nextGeneration = generation + 1;
            long ttl = Math.max(1, Duration.between(Instant.now(), principal.sessionExpiresAt()).toSeconds()
                    + TERMINAL_GRACE_SECONDS);
            hashPut(key, V3Support.map("generation", Integer.toString(nextGeneration), "currentSid", ""), ttl);
            return V3Support.map("authRequestId", authRequestId, "authGeneration", nextGeneration,
                    "sessionId", principal.sessionId(), "revocationState", "REVOKED", "revokedAt", revokedAt);
        } catch (DataAccessException exception) {
            throw authUnavailable();
        }
    }

    public V3Support.V3Principal authenticate(String compact) {
        V3JwtService.VerifiedToken token = jwtService.verify(compact);
        try {
            Map<String, String> login = liveLogin(token.sessionId());
            if (!"ACTIVE".equals(login.get("state")) || !token.accountId().equals(login.get("accountId"))
                    || !token.purpose().equals(login.get("purpose")) || !token.role().equals(login.get("role"))) {
                throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "登录已失效");
            }
            Map<String, Object> account = accountById(token.accountId());
            if (account == null || !token.role().equals(account.get("role"))) {
                throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "登录已失效");
            }
            return new V3Support.V3Principal(token.accountId(), token.sessionId(), token.purpose(), token.role(),
                    account.get("username").toString(), account.get("nickname").toString(), token.sessionExpiresAt());
        } catch (DataAccessException exception) {
            throw authUnavailable();
        }
    }

    public Map<String, Object> accountView(V3Support.V3Principal principal) {
        return V3Support.map("id", principal.accountId(), "username", principal.username(),
                "nickname", principal.nickname(), "role", principal.role());
    }

    public synchronized AnonymousResult createAnonymous(String surface, String laneId, String requestId,
                                                         int expectedGeneration) {
        V3Support.uuid(requestId, "anonymousRequestId");
        try {
            String key = anonymousLaneKey(surface, laneId);
            Map<String, String> lane = hashEntries(key);
            int generation = lane.isEmpty() ? 0 : parseGeneration(lane);
            if (("WEB".equals(surface) && lane.isEmpty()) || generation != expectedGeneration) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "ANONYMOUS_GENERATION_CONFLICT",
                        "匿名会话状态已变化", V3Support.map("kind", "anonymous_generation_conflict",
                                "currentAnonymousGeneration", generation));
            }
            String previousThreadId = lane.get("currentThreadId");
            if (previousThreadId != null && !previousThreadId.isBlank()) {
                expireAnonymous(previousThreadId, "REVOKED");
            }
            String threadId = UUID.randomUUID().toString();
            String credential = V3Support.opaqueToken();
            String digest = V3Support.sha256Ascii(credential);
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant expiresAt = now.plusSeconds(anonymousTtl);
            int nextGeneration = generation + 1;
            long physicalTtl = anonymousCarrierTtl + anonymousTtl;
            hashPut(anonymousSessionKey(threadId), V3Support.map(
                    "credentialDigest", digest, "surface", surface, "laneId", laneId,
                    "ownerKind", "ANONYMOUS", "createdAt", now.toString(), "lastInteractionAt", "",
                    "expiresAt", expiresAt.toString(), "state", "ACTIVE"), physicalTtl);
            redis.opsForValue().set("anon:credential:" + digest, threadId, physicalTtl, TimeUnit.SECONDS);
            hashPut(key, V3Support.map("generation", Integer.toString(nextGeneration),
                    "currentThreadId", threadId), anonymousCarrierTtl);
            return new AnonymousResult(requestId, threadId, nextGeneration, credential, expiresAt,
                    now.plusSeconds(anonymousCarrierTtl));
        } catch (DataAccessException exception) {
            throw authUnavailable();
        }
    }

    public AnonymousProof verifyAnonymous(String credential, String expectedThreadId) {
        try {
            String digest = V3Support.sha256Ascii(credential == null ? "" : credential);
            String threadId = redis.opsForValue().get("anon:credential:" + digest);
            if (threadId == null || (expectedThreadId != null && !expectedThreadId.equals(threadId))) {
                throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "ANONYMOUS_REQUIRED", "匿名会话凭据无效");
            }
            Map<String, String> session = hashEntries(anonymousSessionKey(threadId));
            if (!"ACTIVE".equals(session.get("state")) || !constantEquals(digest, session.get("credentialDigest"))) {
                throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "ANONYMOUS_REVOKED", "匿名会话已撤销");
            }
            Instant expiresAt = Instant.parse(session.get("expiresAt"));
            if (!Instant.now().isBefore(expiresAt)) {
                expireAnonymous(threadId, "EXPIRED");
                throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "ANONYMOUS_EXPIRED", "匿名会话已过期");
            }
            return new AnonymousProof(threadId, session.get("surface"), session.get("laneId"), expiresAt);
        } catch (DataAccessException exception) {
            throw authUnavailable();
        }
    }

    public synchronized AnonymousResult syncAnonymous(String laneId, String requestId, int expectedGeneration,
                                                       Map<String, String> cookies) {
        V3Support.uuid(requestId, "anonymousRequestId");
        try {
            Map<String, String> lane = hashEntries(anonymousLaneKey("WEB", laneId));
            int generation = lane.isEmpty() ? 0 : parseGeneration(lane);
            if (lane.isEmpty() || generation != expectedGeneration) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "ANONYMOUS_GENERATION_CONFLICT",
                        "匿名会话状态已变化", V3Support.map("kind", "anonymous_generation_conflict",
                                "currentAnonymousGeneration", generation));
            }
            String threadId = lane.get("currentThreadId");
            String credential = cookies.get(anonymousCookieName(threadId));
            AnonymousProof proof = verifyAnonymous(credential, threadId);
            return new AnonymousResult(requestId, threadId, generation, credential, proof.expiresAt(),
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(anonymousCarrierTtl));
        } catch (DataAccessException exception) {
            throw authUnavailable();
        }
    }

    public synchronized Map<String, Object> recordAnonymousInteraction(String credential, String threadId,
                                                                       String runId) {
        V3Support.uuid(threadId, "threadId");
        V3Support.uuid(runId, "runId");
        AnonymousProof proof = verifyAnonymous(credential, threadId);
        String acceptedKey = "anon:accepted-runs:" + threadId;
        Boolean first = redis.opsForSet().add(acceptedKey, runId) == 1;
        Map<String, String> session = hashEntries(anonymousSessionKey(threadId));
        String interactionAt = session.get("lastInteractionAt");
        Instant expiresAt = proof.expiresAt();
        if (first) {
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            expiresAt = now.plusSeconds(anonymousTtl);
            redis.opsForHash().put(anonymousSessionKey(threadId), "lastInteractionAt", now.toString());
            redis.opsForHash().put(anonymousSessionKey(threadId), "expiresAt", expiresAt.toString());
            redis.expire(acceptedKey, anonymousCarrierTtl + anonymousTtl, TimeUnit.SECONDS);
            interactionAt = now.toString();
        }
        return V3Support.map("threadId", threadId, "runId", runId, "lastInteractionAt", interactionAt,
                "sessionExpiresAt", expiresAt.toString(), "replayed", !first);
    }

    private LoginResult loginResult(Map<String, Object> account, String authRequestId, int generation,
                                    String sessionId, String purpose, String refreshToken,
                                    Instant absoluteExpiresAt, boolean refresh) {
        V3JwtService.IssuedToken token = jwtService.issue(account.get("id").toString(), sessionId, purpose,
                account.get("role").toString(), absoluteExpiresAt);
        LinkedHashMap<String, Object> data = V3Support.map(
                "authRequestId", authRequestId, "authGeneration", generation, "sessionId", sessionId,
                "purpose", purpose, "account", V3Support.map("id", account.get("id"),
                        "username", account.get("username"), "nickname", account.get("nickname"),
                        "role", account.get("role")), "tokenType", "Bearer", "accessToken", token.accessToken(),
                "accessExpiresAt", token.expiresAt().toString(), "loginExpiresAt", absoluteExpiresAt.toString());
        return new LoginResult(data, refreshToken, sessionId, absoluteExpiresAt, refresh);
    }

    private Map<String, Object> accountByUsername(String username) {
        return jdbc.queryForList("SELECT id, username, nickname, role, password_hash FROM platform_account WHERE username = ?",
                username).stream().findFirst().orElse(null);
    }

    private Map<String, Object> accountById(String id) {
        return jdbc.queryForList("SELECT id, username, nickname, role, password_hash FROM platform_account WHERE id = ?",
                id).stream().findFirst().orElse(null);
    }

    private Map<String, String> liveLogin(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录");
        }
        Map<String, String> login = hashEntries(loginKey(sessionId));
        if (login.isEmpty()) {
            throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "登录状态无效或已过期");
        }
        if (!"ACTIVE".equals(login.get("state"))) {
            throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "登录已失效");
        }
        Instant expiresAt = Instant.parse(login.get("absoluteExpiresAt"));
        if (!Instant.now().isBefore(expiresAt)) {
            revoke(sessionId, "EXPIRED");
            throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED", "登录已过期");
        }
        return login;
    }

    private void revoke(String sessionId, String state) {
        Map<String, String> login = hashEntries(loginKey(sessionId));
        if (login.isEmpty()) {
            return;
        }
        String now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        redis.opsForHash().put(loginKey(sessionId), "state", state);
        redis.opsForHash().put(loginKey(sessionId), "terminalAt", now);
        String digest = login.get("refreshDigest");
        if (digest != null) {
            redis.delete(refreshKey(login.get("purpose"), digest));
            hashPut("auth:terminal:refresh:" + login.get("purpose") + ":" + digest,
                    V3Support.map("state", state, "terminalAt", now,
                            "absoluteExpiresAt", login.get("absoluteExpiresAt")), TERMINAL_GRACE_SECONDS);
        }
    }

    private void expireAnonymous(String threadId, String state) {
        Map<String, String> session = hashEntries(anonymousSessionKey(threadId));
        if (session.isEmpty()) {
            return;
        }
        String digest = session.get("credentialDigest");
        redis.opsForHash().put(anonymousSessionKey(threadId), "state", state);
        redis.delete("anon:credential:" + digest);
        hashPut("anon:terminal:" + digest, V3Support.map("state", state,
                "terminalAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), "threadId", threadId),
                anonymousCarrierTtl);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> hashEntries(String key) {
        Map<Object, Object> values = redis.opsForHash().entries(key);
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        values.forEach((name, value) -> result.put(name.toString(), value.toString()));
        return result;
    }

    private void hashPut(String key, Map<String, Object> values, long ttlSeconds) {
        Map<String, String> text = new LinkedHashMap<>();
        values.forEach((name, value) -> text.put(name, value == null ? "" : value.toString()));
        redis.opsForHash().putAll(key, text);
        redis.expire(key, Math.max(1, ttlSeconds), TimeUnit.SECONDS);
    }

    private static int parseGeneration(Map<String, String> lane) {
        return Integer.parseInt(lane.getOrDefault("generation", "0"));
    }

    private static boolean constantEquals(String left, String right) {
        byte[] a = left == null ? new byte[0] : left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right == null ? new byte[0] : right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private static String laneKey(String surface, String purpose, String laneId) {
        return "auth:lane:" + surface + ":" + purpose + ":" + laneId;
    }

    private static String loginKey(String sid) {
        return "auth:login:" + sid;
    }

    private static String refreshKey(String purpose, String digest) {
        return "auth:refresh:" + purpose + ":" + digest;
    }

    public static String refreshCookieName(String purpose, String sessionId) {
        return ("ADMIN".equals(purpose) ? "WD_ADMIN_REFRESH_" : "WD_WEB_REFRESH_")
                + sessionId.replace("-", "");
    }

    public static String anonymousCookieName(String threadId) {
        return "WD_ANON_" + threadId.replace("-", "");
    }

    private static String anonymousLaneKey(String surface, String laneId) {
        return "anon:lane:" + surface + ":" + laneId;
    }

    private static String anonymousSessionKey(String threadId) {
        return "anon:session:" + threadId;
    }

    private static ApiRequestException generationConflict(int current) {
        return new ApiRequestException(HttpStatus.CONFLICT, "AUTH_GENERATION_CONFLICT", "登录状态已变化",
                V3Support.map("kind", "auth_generation_conflict", "currentAuthGeneration", current));
    }

    private static ApiRequestException authUnavailable() {
        return new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_STATE_UNAVAILABLE", "登录状态服务暂时不可用");
    }

    public record LaneBootstrap(String laneId, int generation) {
    }

    public record LoginResult(Map<String, Object> data, String refreshToken, String sessionId,
                              Instant loginExpiresAt, boolean refresh) {
    }

    public record AnonymousResult(String requestId, String threadId, int generation, String credential,
                                  Instant sessionExpiresAt, Instant carrierExpiresAt) {
    }

    public record AnonymousProof(String threadId, String surface, String laneId, Instant expiresAt) {
    }
}
