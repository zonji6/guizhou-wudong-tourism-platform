package com.guizhou.wudong.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.guizhou.wudong.domain.PostType;
import com.guizhou.wudong.domain.RouteNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CommunityRequests {
    private static final Set<String> POST_FIELDS = Set.of(
            "postType", "title", "content", "tags", "routeSummary", "routeNodes");
    private static final Set<String> ROUTE_NODE_FIELDS = Set.of(
            "sequence", "placeId", "placeName", "note");

    private CommunityRequests() {
    }

    public record PostCreate(
            PostType postType,
            String title,
            String content,
            List<String> tags,
            String routeSummary,
            List<RouteNode> routeNodes
    ) {
    }

    public static PostCreate post(JsonNode body) {
        strictObject(body, POST_FIELDS);
        PostType postType = postType(body);
        String content = requiredText(body, "content", 16_000);
        List<String> tags = tags(body);
        if (postType == PostType.MOMENT) {
            requireAbsentOrNull(body, "title");
            requireAbsentOrNull(body, "routeSummary");
            requireAbsentOrNull(body, "routeNodes");
            return new PostCreate(postType, null, content, tags, null, null);
        }
        String title = requiredText(body, "title", 180);
        String routeSummary = requiredText(body, "routeSummary", 500);
        List<RouteNode> routeNodes = routeNodes(body);
        return new PostCreate(postType, title, content, tags, routeSummary, routeNodes);
    }

    private static PostType postType(JsonNode body) {
        String value = requiredExactText(body, "postType", 32);
        try {
            return PostType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("动态类型无效");
        }
    }

    private static List<String> tags(JsonNode body) {
        if (!body.has("tags")) {
            return List.of();
        }
        JsonNode node = body.get("tags");
        if (node == null || !node.isArray()) {
            throw new IllegalArgumentException("标签必须是数组");
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = exactText(item, 80);
            if (value.contains(",") || !values.add(value)) {
                throw new IllegalArgumentException("标签无效或重复");
            }
        }
        if (String.join(",", values).length() > 500) {
            throw new IllegalArgumentException("标签总长度不能超过 500 个字符");
        }
        return List.copyOf(values);
    }

    private static List<RouteNode> routeNodes(JsonNode body) {
        JsonNode nodes = body.get("routeNodes");
        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            throw new IllegalArgumentException("路线攻略至少需要一个节点");
        }
        List<RouteNode> result = new ArrayList<>();
        Set<Integer> sequences = new HashSet<>();
        for (JsonNode node : nodes) {
            strictObject(node, ROUTE_NODE_FIELDS);
            if (!node.has("sequence") || !node.has("placeId") || !node.hasNonNull("placeName")
                    || !node.hasNonNull("note")) {
                throw new IllegalArgumentException("路线节点字段不完整");
            }
            int sequence = positiveInteger(node.get("sequence"));
            if (!sequences.add(sequence)) {
                throw new IllegalArgumentException("路线节点序号不能重复");
            }
            String placeId = node.get("placeId").isNull()
                    ? null
                    : OrderRequests.canonicalUuid(exactText(node.get("placeId"), 36));
            result.add(new RouteNode(sequence, placeId, text(node.get("placeName"), 120),
                    text(node.get("note"), 500)));
        }
        result.sort(Comparator.comparingInt(RouteNode::sequence));
        return List.copyOf(result);
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

    private static void requireAbsentOrNull(JsonNode body, String field) {
        if (body.has(field) && !body.get(field).isNull()) {
            throw new IllegalArgumentException("普通动态不能包含路线字段");
        }
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
        return exactText(body.get(field), maxLength);
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

    private static String exactText(JsonNode node, int maxLength) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()
                || !node.textValue().equals(node.textValue().trim()) || node.textValue().length() > maxLength) {
            throw new IllegalArgumentException("精确文本字段无效");
        }
        return node.textValue();
    }

    private static int positiveInteger(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() <= 0) {
            throw new IllegalArgumentException("路线节点序号必须为正整数");
        }
        return node.intValue();
    }
}
