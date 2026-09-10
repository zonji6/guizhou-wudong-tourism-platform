package com.guizhou.wudong.v3;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guizhou.wudong.api.ApiRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class V3CandidateClient {
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String credentialFile;
    private final HttpClient httpClient;

    public V3CandidateClient(ObjectMapper objectMapper,
                             @Value("${wudong.internal.ai-base-url:http://127.0.0.1:8002}") String baseUrl,
                             @Value("${wudong.internal.java-to-ai-credential-file:}") String credentialFile) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.credentialFile = credentialFile;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public Map<String, Object> resolve(Map<String, Object> candidateRef, String candidateType, String action,
                                       Map<String, Object> expectedBase, String userProof, String anonymousProof) {
        validateCandidateRef(candidateRef);
        Map<String, Object> requestBody = V3Support.map("candidateRef", candidateRef,
                "expectedCandidateType", candidateType, "expectedAction", action, "expectedBase", expectedBase);
        String credential = readCredential();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/internal/assistant/candidates/resolve"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Wudong-Contract", V3Support.CONTRACT)
                    .header("X-Wudong-Service-Credential", credential)
                    .header("X-Wudong-User-Proof", userProof)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));
            if (anonymousProof != null && !anonymousProof.isBlank()) {
                builder.header("X-Wudong-Anonymous-Proof", anonymousProof);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                rethrowKnown(response);
            }
            Map<String, Object> envelope = objectMapper.readValue(response.body(), new TypeReference<>() { });
            V3Support.onlyKeys(envelope, "success", "data", "message", "code", "details");
            if (!Boolean.TRUE.equals(envelope.get("success")) || !(envelope.get("data") instanceof Map<?, ?>)) {
                throw unavailable();
            }
            @SuppressWarnings("unchecked") Map<String, Object> data = (Map<String, Object>) envelope.get("data");
            validateResponse(data, candidateRef, candidateType, action, expectedBase);
            return data;
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private void validateResponse(Map<String, Object> data, Map<String, Object> expectedRef,
                                  String expectedType, String expectedAction, Map<String, Object> expectedBase) {
        V3Support.onlyKeys(data, "candidateRef", "candidateType", "adoptionAction", "baseResource",
                "sourceOwnerKind", "createdAt", "expiresAt", "candidateDigest", "knowledgeContext",
                "knowledgeDependencies", "payload", "resolvedAt");
        Map<String, Object> actualRef = V3Support.object(data, "candidateRef");
        validateCandidateRef(actualRef);
        if (!V3Support.canonicalJson(expectedRef).equals(V3Support.canonicalJson(actualRef))
                || !expectedType.equals(data.get("candidateType")) || !expectedAction.equals(data.get("adoptionAction"))) {
            throw invalidPayload();
        }
        Object actualBase = data.get("baseResource");
        if (!V3Support.canonicalJson(expectedBase).equals(V3Support.canonicalJson(actualBase))) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "CANDIDATE_BASE_MISMATCH", "候选绑定的基础资源不匹配");
        }
        if (!SetSupport.ownerKind(data.get("sourceOwnerKind")) || !(data.get("payload") instanceof Map<?, ?>)
                || !(data.get("knowledgeDependencies") instanceof java.util.List<?>)) {
            throw invalidPayload();
        }
        Instant expiresAt;
        try {
            expiresAt = Instant.parse((String) data.get("expiresAt"));
            Instant.parse((String) data.get("createdAt"));
            Instant.parse((String) data.get("resolvedAt"));
        } catch (Exception exception) {
            throw invalidPayload();
        }
        if (Instant.now().isAfter(expiresAt)) {
            throw new ApiRequestException(HttpStatus.GONE, "CANDIDATE_EXPIRED", "候选版本已过期");
        }
        String expectedDigest = V3Support.digest(data, "candidateDigest");
        Map<String, Object> material = V3Support.map("contractVersion", V3Support.CONTRACT,
                "candidateRef", data.get("candidateRef"), "candidateType", data.get("candidateType"),
                "adoptionAction", data.get("adoptionAction"), "baseResource", data.get("baseResource"),
                "sourceOwnerKind", data.get("sourceOwnerKind"), "createdAt", data.get("createdAt"),
                "expiresAt", data.get("expiresAt"), "knowledgeContext", data.get("knowledgeContext"),
                "knowledgeDependencies", data.get("knowledgeDependencies"), "payload", data.get("payload"));
        if (!expectedDigest.equals(V3Support.sha256(material))) {
            throw invalidPayload();
        }
    }

    public static void validateCandidateRef(Map<String, Object> value) {
        V3Support.onlyKeys(value, "threadId", "candidateId", "candidateVersion");
        V3Support.uuid(value, "threadId");
        V3Support.uuid(value, "candidateId");
        V3Support.positiveInt(value, "candidateVersion");
    }

    private String readCredential() {
        if (credentialFile == null || credentialFile.isBlank()) {
            throw unavailable();
        }
        try {
            String value = Files.readString(Path.of(credentialFile), StandardCharsets.US_ASCII).stripTrailing();
            if (!value.matches("^[A-Za-z0-9_-]{43}$")) {
                throw unavailable();
            }
            return value;
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private void rethrowKnown(HttpResponse<String> response) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(response.body(), new TypeReference<>() { });
            Object code = envelope.get("code");
            if (code instanceof String stable && java.util.Set.of("CANDIDATE_EXPIRED",
                    "CANDIDATE_VERSION_UNAVAILABLE", "CANDIDATE_PAYLOAD_INVALID",
                    "CANDIDATE_BASE_MISMATCH").contains(stable)) {
                HttpStatus status = stable.startsWith("CANDIDATE_VERSION") || stable.endsWith("EXPIRED")
                        ? HttpStatus.GONE : HttpStatus.CONFLICT;
                throw new ApiRequestException(status, stable, "候选版本不可用于本次采用");
            }
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception ignored) {
            // 上游非规范错误统一收敛，不回显正文。
        }
        throw unavailable();
    }

    private static ApiRequestException invalidPayload() {
        return new ApiRequestException(HttpStatus.CONFLICT, "CANDIDATE_PAYLOAD_INVALID", "候选载荷校验失败");
    }

    private static ApiRequestException unavailable() {
        return new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "CANDIDATE_LOOKUP_UNAVAILABLE",
                "候选暂时无法核验");
    }

    private static final class SetSupport {
        private static boolean ownerKind(Object value) {
            return "USER".equals(value) || "ANONYMOUS".equals(value);
        }
    }
}
