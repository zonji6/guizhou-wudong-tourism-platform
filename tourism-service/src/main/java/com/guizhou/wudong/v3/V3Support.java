package com.guizhou.wudong.v3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.guizhou.wudong.api.ApiRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class V3Support {
    public static final String CONTRACT = "tourism-api-v3-draft-r3";
    public static final String NOTICE = "本机模拟核价，不代表真实报价、库存、餐位或房态。";
    // 目录资料使用稳定的 UUID 形状 ID；它们不一定携带随机 UUID 的版本／变体位。
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern DIGEST_PATTERN = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,32}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(?=.*[0-9])[0-9 +()\\-]{1,32}$");
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{1,64}$");
    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private V3Support() {
    }

    public static LinkedHashMap<String, Object> map(Object... values) {
        if (values.length % 2 != 0) {
            throw new IllegalArgumentException("键值参数必须成对");
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    public static void onlyKeys(Map<String, Object> body, String... allowed) {
        Set<String> whitelist = Set.of(allowed);
        for (String key : body.keySet()) {
            if (!whitelist.contains(key)) {
                bad("请求包含未定义字段");
            }
        }
    }

    public static String requiredText(Map<String, Object> body, String key, int min, int max) {
        if (!body.containsKey(key) || !(body.get(key) instanceof String raw)) {
            bad("字段 " + key + " 必填且必须是字符串");
        }
        String value = ((String) body.get(key)).trim();
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) {
            bad("字段 " + key + " 长度不符合要求");
        }
        return value;
    }

    public static String rawRequiredText(Map<String, Object> body, String key) {
        if (!body.containsKey(key) || !(body.get(key) instanceof String value)) {
            bad("字段 " + key + " 必填且必须是字符串");
        }
        return (String) body.get(key);
    }

    public static String optionalText(Map<String, Object> body, String key, int max) {
        if (!body.containsKey(key) || body.get(key) == null) {
            return null;
        }
        if (!(body.get(key) instanceof String raw)) {
            bad("字段 " + key + " 必须是字符串或 null");
        }
        String value = ((String) body.get(key)).trim();
        if (value.codePointCount(0, value.length()) > max) {
            bad("字段 " + key + " 过长");
        }
        return value.isEmpty() ? null : value;
    }

    public static String username(Map<String, Object> body) {
        String value = rawRequiredText(body, "username");
        if (!USERNAME_PATTERN.matcher(value).matches()) {
            bad("用户名格式不符合要求");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static String password(Map<String, Object> body) {
        String value = rawRequiredText(body, "password");
        if (value.codePointCount(0, value.length()) < 8) {
            bad("密码至少需要 8 个字符");
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiRequestException(HttpStatus.BAD_REQUEST, "PASSWORD_ENCODING_LIMIT_EXCEEDED",
                    "密码编码后不能超过 72 字节，请缩短后重试。",
                    map("kind", "password_encoding_limit", "encoding", "UTF-8", "maxBytes", 72));
        }
        return value;
    }

    public static int positiveInt(Map<String, Object> body, String key) {
        if (!body.containsKey(key) || !(body.get(key) instanceof Number number)) {
            bad("字段 " + key + " 必须是正整数");
        }
        long value = ((Number) body.get(key)).longValue();
        if (value <= 0 || value > Integer.MAX_VALUE || ((Number) body.get(key)).doubleValue() != value) {
            bad("字段 " + key + " 必须是正整数");
        }
        return (int) value;
    }

    public static int nonNegativeInt(Map<String, Object> body, String key) {
        if (!body.containsKey(key) || !(body.get(key) instanceof Number number)) {
            bad("字段 " + key + " 必须是非负整数");
        }
        long value = ((Number) body.get(key)).longValue();
        if (value < 0 || value > Integer.MAX_VALUE || ((Number) body.get(key)).doubleValue() != value) {
            bad("字段 " + key + " 必须是非负整数");
        }
        return (int) value;
    }

    public static String uuid(Map<String, Object> body, String key) {
        return uuid(rawRequiredText(body, key), key);
    }

    public static String optionalUuid(Map<String, Object> body, String key) {
        if (!body.containsKey(key) || body.get(key) == null) {
            return null;
        }
        if (!(body.get(key) instanceof String value)) {
            bad("字段 " + key + " 必须是规范 UUID 或 null");
        }
        return uuid((String) body.get(key), key);
    }

    public static String uuid(String value, String key) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            bad("字段 " + key + " 必须是小写规范 UUID");
        }
        return value;
    }

    public static String digest(Map<String, Object> body, String key) {
        String value = rawRequiredText(body, key);
        if (!DIGEST_PATTERN.matcher(value).matches()) {
            bad("字段 " + key + " 必须是 SHA-256 指纹");
        }
        return value;
    }

    public static String phone(Map<String, Object> body) {
        String value = requiredText(body, "contactPhone", 1, 32);
        if (!PHONE_PATTERN.matcher(value).matches()) {
            bad("联系人电话格式不符合要求");
        }
        return value;
    }

    public static LocalDate date(Map<String, Object> body, String key) {
        String value = rawRequiredText(body, key);
        try {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                bad("字段 " + key + " 必须是 YYYY-MM-DD");
            }
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            bad("字段 " + key + " 不是有效日期");
            return null;
        }
    }

    public static LocalDateTime dateTime(Map<String, Object> body, String key) {
        String value = rawRequiredText(body, key);
        try {
            if (!value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
                bad("字段 " + key + " 必须是无时区的秒精度本地时间");
            }
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException exception) {
            bad("字段 " + key + " 不是有效时间");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Map<String, Object> body, String key) {
        if (!body.containsKey(key) || !(body.get(key) instanceof Map<?, ?> value)) {
            bad("字段 " + key + " 必须是对象");
        }
        return (Map<String, Object>) body.get(key);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objectList(Map<String, Object> body, String key, int min, int max) {
        if (!body.containsKey(key) || !(body.get(key) instanceof List<?> values)
                || values.size() < min || values.size() > max) {
            bad("字段 " + key + " 数组长度不符合要求");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : (List<?>) body.get(key)) {
            if (!(value instanceof Map<?, ?> item)) {
                bad("字段 " + key + " 的元素必须是对象");
            }
            result.add((Map<String, Object>) value);
        }
        return result;
    }

    public static List<String> stringList(Map<String, Object> body, String key, int maxItems, int maxLength) {
        if (!body.containsKey(key)) {
            return List.of();
        }
        if (!(body.get(key) instanceof List<?> values) || values.size() > maxItems) {
            bad("字段 " + key + " 必须是数组");
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object raw : (List<?>) body.get(key)) {
            if (!(raw instanceof String value)) {
                bad("字段 " + key + " 的元素必须是字符串");
            }
            String normalized = ((String) raw).trim();
            int length = normalized.codePointCount(0, normalized.length());
            if (length < 1 || length > maxLength || !seen.add(normalized)) {
                bad("字段 " + key + " 包含空值、重复值或过长元素");
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }

    public static String tagsCsv(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return null;
        }
        return raw.toString();
    }

    public static List<String> tags(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return List.of();
        }
        return List.of(raw.toString().split(",")).stream().map(String::trim).filter(v -> !v.isEmpty()).toList();
    }

    public static String money(Object raw) {
        if (raw == null) {
            return null;
        }
        return new BigDecimal(raw.toString()).setScale(2).toPlainString();
    }

    public static String canonicalJson(Object value) {
        try {
            return CANONICAL.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("规范 JSON 序列化失败", exception);
        }
    }

    public static String sha256(Object value) {
        return "sha256:" + hex(sha256Bytes(canonicalJson(value).getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256Ascii(String value) {
        return hex(sha256Bytes(value.getBytes(StandardCharsets.US_ASCII)));
    }

    private static byte[] sha256Bytes(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    public static String opaqueToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String now() {
        return Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
    }

    public static String utc(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Timestamp timestamp) {
            return timestamp.toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        }
        if (raw instanceof LocalDateTime dateTime) {
            return dateTime.toInstant(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        }
        if (raw instanceof Instant instant) {
            return instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        }
        return raw.toString();
    }

    public static V3Principal principal(String purpose) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof V3Principal principal)
                || !purpose.equals(principal.purpose())) {
            throw new ApiRequestException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "请先使用正确账号登录");
        }
        return principal;
    }

    public static void bad(String message) {
        throw new ApiRequestException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    public static void notFound() {
        throw new ApiRequestException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "未找到或无权访问该资源");
    }

    public static void conflict(String code, String message) {
        throw new ApiRequestException(HttpStatus.CONFLICT, code, message);
    }

    public static boolean terminalRunState(String state) {
        return Set.of("STOPPED", "INTERRUPTED", "COMPLETED", "FAILED").contains(state);
    }

    public record V3Principal(String accountId, String sessionId, String purpose, String role,
                              String username, String nickname, Instant sessionExpiresAt) {
    }
}
