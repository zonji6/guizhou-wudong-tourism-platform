package com.guizhou.wudong.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;

public final class OrderRequests {
    private static final Set<String> PRODUCT_FIELDS = Set.of(
            "productId", "quantity", "pickupPoint", "contactName", "contactPhone", "note", "sourceThreadId");
    private static final Set<String> FOOD_FIELDS = Set.of(
            "foodItemId", "visitAt", "peopleCount", "contactName", "contactPhone", "note", "sourceThreadId");
    private static final Set<String> STAY_FIELDS = Set.of(
            "roomTypeId", "checkInDate", "peopleCount", "contactName", "contactPhone", "note", "sourceThreadId");
    private static final Set<String> STATUS_FIELDS = Set.of("status");

    private OrderRequests() {
    }

    public record ProductCreate(
            String productId,
            int quantity,
            String pickupPoint,
            String contactName,
            String contactPhone,
            String note,
            String sourceThreadId
    ) {
    }

    public record FoodCreate(
            String foodItemId,
            LocalDateTime visitAt,
            int peopleCount,
            String contactName,
            String contactPhone,
            String note,
            String sourceThreadId
    ) {
    }

    public record StayCreate(
            String roomTypeId,
            LocalDate checkInDate,
            int peopleCount,
            String contactName,
            String contactPhone,
            String note,
            String sourceThreadId
    ) {
    }

    public static ProductCreate product(JsonNode body) {
        strictObject(body, PRODUCT_FIELDS);
        return new ProductCreate(
                canonicalUuid(requiredExactText(body, "productId", 36)),
                positiveInteger(body, "quantity"),
                requiredExactText(body, "pickupPoint", 160),
                requiredText(body, "contactName", 80),
                requiredText(body, "contactPhone", 32),
                nullableText(body, "note", 500),
                nullableText(body, "sourceThreadId", 100));
    }

    public static FoodCreate food(JsonNode body) {
        strictObject(body, FOOD_FIELDS);
        return new FoodCreate(
                canonicalUuid(requiredExactText(body, "foodItemId", 36)),
                localDateTime(body, "visitAt"),
                positiveInteger(body, "peopleCount"),
                requiredText(body, "contactName", 80),
                requiredText(body, "contactPhone", 32),
                nullableText(body, "note", 500),
                nullableText(body, "sourceThreadId", 100));
    }

    public static StayCreate stay(JsonNode body) {
        strictObject(body, STAY_FIELDS);
        return new StayCreate(
                canonicalUuid(requiredExactText(body, "roomTypeId", 36)),
                localDate(body, "checkInDate"),
                positiveInteger(body, "peopleCount"),
                requiredText(body, "contactName", 80),
                requiredText(body, "contactPhone", 32),
                nullableText(body, "note", 500),
                nullableText(body, "sourceThreadId", 100));
    }

    public static String status(JsonNode body) {
        strictObject(body, STATUS_FIELDS);
        return requiredExactText(body, "status", 32);
    }

    public static String canonicalUuid(String value) {
        try {
            String normalized = UUID.fromString(value).toString();
            if (!normalized.equals(value)) {
                throw new IllegalArgumentException("标识必须为规范 UUID");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("标识必须为规范 UUID");
        }
    }

    private static void strictObject(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("请求体必须是对象");
        }
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("存在不允许的字段");
            }
        });
    }

    private static String requiredText(JsonNode body, String field, int maxLength) {
        if (!body.hasNonNull(field)) {
            throw new IllegalArgumentException("缺少必填字段");
        }
        return text(body.get(field), maxLength);
    }

    private static String requiredExactText(JsonNode body, String field, int maxLength) {
        if (!body.hasNonNull(field)) {
            throw new IllegalArgumentException("缺少必填字段");
        }
        JsonNode node = body.get(field);
        if (!node.isTextual() || node.textValue().isBlank() || !node.textValue().equals(node.textValue().trim())
                || node.textValue().length() > maxLength) {
            throw new IllegalArgumentException("精确文本字段无效");
        }
        return node.textValue();
    }

    private static String nullableText(JsonNode body, String field, int maxLength) {
        if (!body.has(field) || body.get(field).isNull()) {
            return null;
        }
        return text(body.get(field), maxLength);
    }

    private static String text(JsonNode node, int maxLength) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException("文本字段无效");
        }
        String value = node.textValue().trim();
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("文本字段超过长度限制");
        }
        return value;
    }

    private static int positiveInteger(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || !node.isIntegralNumber() || !node.canConvertToInt()
                || node.intValue() <= 0) {
            throw new IllegalArgumentException("整数必须为正数");
        }
        return node.intValue();
    }

    private static LocalDateTime localDateTime(JsonNode body, String field) {
        String value = requiredExactText(body, field, 32);
        try {
            LocalDateTime parsed = LocalDateTime.parse(value);
            if (parsed.getYear() < 1000 || parsed.getYear() > 9999 || parsed.getNano() != 0) {
                throw new IllegalArgumentException("时间范围或精度无效");
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("时间格式无效");
        }
    }

    private static LocalDate localDate(JsonNode body, String field) {
        String value = requiredExactText(body, field, 10);
        try {
            LocalDate parsed = LocalDate.parse(value);
            if (parsed.getYear() < 1000 || parsed.getYear() > 9999) {
                throw new IllegalArgumentException("日期范围无效");
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("日期格式无效");
        }
    }
}
