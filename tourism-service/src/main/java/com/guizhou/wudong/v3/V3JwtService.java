package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiRequestException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
public class V3JwtService {
    private final String issuer;
    private final String keyId;
    private final String privateKeyFile;
    private final String publicKeyFile;
    private final long accessTtlSeconds;
    private volatile RSAPrivateKey privateKey;
    private volatile RSAPublicKey publicKey;

    public V3JwtService(
            @Value("${wudong.auth.issuer}") String issuer,
            @Value("${wudong.auth.key-id:}") String keyId,
            @Value("${wudong.auth.private-key-file:}") String privateKeyFile,
            @Value("${wudong.auth.public-key-file:}") String publicKeyFile,
            @Value("${wudong.auth.access-ttl-seconds:900}") long accessTtlSeconds) {
        this.issuer = issuer;
        this.keyId = keyId;
        this.privateKeyFile = privateKeyFile;
        this.publicKeyFile = publicKeyFile;
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public IssuedToken issue(String accountId, String sessionId, String purpose, String role, Instant sessionExpiresAt) {
        requireConfiguration(true);
        try {
            Instant issuedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            Instant expiresAt = issuedAt.plusSeconds(accessTtlSeconds);
            if (expiresAt.isAfter(sessionExpiresAt)) {
                expiresAt = sessionExpiresAt;
            }
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(accountId)
                    .audience("USER".equals(purpose) ? List.of("wudong-java", "wudong-ai") : List.of("wudong-java"))
                    .issueTime(Date.from(issuedAt))
                    .notBeforeTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .claim("session_exp", sessionExpiresAt.getEpochSecond())
                    .claim("sid", sessionId)
                    .claim("purpose", purpose)
                    .claim("role", role)
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT).keyID(keyId).build(), claims);
            jwt.sign(new RSASSASigner(privateKey()));
            return new IssuedToken(jwt.serialize(), expiresAt);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    public VerifiedToken verify(String compact) {
        requireConfiguration(false);
        try {
            SignedJWT jwt = SignedJWT.parse(compact);
            JWSHeader header = jwt.getHeader();
            if (!JWSAlgorithm.RS256.equals(header.getAlgorithm()) || !JOSEObjectType.JWT.equals(header.getType())
                    || !keyId.equals(header.getKeyID()) || !jwt.verify(new RSASSAVerifier(publicKey()))) {
                throw invalid();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = Instant.now();
            Instant issuedAt = requiredDate(claims.getIssueTime()).toInstant();
            Instant notBefore = requiredDate(claims.getNotBeforeTime()).toInstant();
            Instant expiresAt = requiredDate(claims.getExpirationTime()).toInstant();
            Number sessionExpiration = (Number) claims.getClaim("session_exp");
            String subject = claims.getSubject();
            String sessionId = claims.getStringClaim("sid");
            String purpose = claims.getStringClaim("purpose");
            String role = claims.getStringClaim("role");
            List<String> expectedAudience = "USER".equals(purpose)
                    ? List.of("wudong-java", "wudong-ai") : List.of("wudong-java");
            if (subject == null || sessionId == null || sessionExpiration == null
                    || !V3Support.CONTRACT.equals(V3Support.CONTRACT)
                    || !issuer.equals(claims.getIssuer()) || !issuedAt.equals(notBefore)
                    || now.isBefore(notBefore) || !now.isBefore(expiresAt)
                    || !now.isBefore(Instant.ofEpochSecond(sessionExpiration.longValue()))
                    || !("USER".equals(purpose) || "ADMIN".equals(purpose))
                    || !purpose.equals(role) || !claims.getAudience().equals(expectedAudience)) {
                throw invalid();
            }
            V3Support.uuid(subject, "sub");
            V3Support.uuid(sessionId, "sid");
            return new VerifiedToken(subject, sessionId, purpose, role, issuedAt, expiresAt,
                    Instant.ofEpochSecond(sessionExpiration.longValue()));
        } catch (ApiRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static Date requiredDate(Date value) {
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    private RSAPrivateKey privateKey() throws Exception {
        RSAPrivateKey cached = privateKey;
        if (cached == null) {
            synchronized (this) {
                cached = privateKey;
                if (cached == null) {
                    cached = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                            .generatePrivate(new PKCS8EncodedKeySpec(readPem(privateKeyFile, "PRIVATE KEY")));
                    if (cached.getModulus().bitLength() < 2048) {
                        throw new IllegalStateException("RSA 密钥长度不足");
                    }
                    privateKey = cached;
                }
            }
        }
        return cached;
    }

    private RSAPublicKey publicKey() throws Exception {
        RSAPublicKey cached = publicKey;
        if (cached == null) {
            synchronized (this) {
                cached = publicKey;
                if (cached == null) {
                    cached = (RSAPublicKey) KeyFactory.getInstance("RSA")
                            .generatePublic(new X509EncodedKeySpec(readPem(publicKeyFile, "PUBLIC KEY")));
                    if (cached.getModulus().bitLength() < 2048) {
                        throw new IllegalStateException("RSA 密钥长度不足");
                    }
                    publicKey = cached;
                }
            }
        }
        return cached;
    }

    private static byte[] readPem(String path, String type) throws Exception {
        String value = Files.readString(Path.of(path), StandardCharsets.US_ASCII)
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(value);
    }

    private void requireConfiguration(boolean needsPrivateKey) {
        if (keyId.isBlank() || publicKeyFile.isBlank() || (needsPrivateKey && privateKeyFile.isBlank())) {
            throw unavailable();
        }
    }

    private static ApiRequestException invalid() {
        return new ApiRequestException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "登录状态无效或已过期");
    }

    private static ApiRequestException unavailable() {
        return new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_STATE_UNAVAILABLE", "登录服务暂时不可用");
    }

    public record IssuedToken(String accessToken, Instant expiresAt) {
    }

    public record VerifiedToken(String accountId, String sessionId, String purpose, String role,
                                Instant issuedAt, Instant expiresAt, Instant sessionExpiresAt) {
    }
}
