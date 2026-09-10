package com.guizhou.wudong.v3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class V3CommunityService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public V3CommunityService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list(String type, String tag) {
        if (type != null && !Set.of("MOMENT", "ROUTE_GUIDE").contains(type)) {
            V3Support.bad("type 查询参数无效");
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM community_post WHERE published=true");
        List<Object> values = new ArrayList<>();
        if (type != null) {
            sql.append(" AND post_type=?");
            values.add(type);
        }
        if (tag != null) {
            if (tag.isBlank()) {
                V3Support.bad("tag 不能为空");
            }
            sql.append(" AND FIND_IN_SET(?,tags)>0");
            values.add(tag);
        }
        sql.append(" ORDER BY published_at DESC,id DESC");
        return jdbc.queryForList(sql.toString(), values.toArray()).stream().map(this::view).toList();
    }

    public Map<String, Object> get(String id) {
        V3Support.uuid(id, "id");
        Map<String, Object> row = jdbc.queryForList("SELECT * FROM community_post WHERE id=? AND published=true", id)
                .stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return view(row);
    }

    @Transactional
    public Map<String, Object> create(V3Support.V3Principal principal, Map<String, Object> body) {
        String type = V3Support.rawRequiredText(body, "postType");
        String title;
        String routeSummary;
        List<Map<String, Object>> routeNodes;
        if ("MOMENT".equals(type)) {
            V3Support.onlyKeys(body, "postType", "content", "tags");
            title = null;
            routeSummary = null;
            routeNodes = List.of();
        } else if ("ROUTE_GUIDE".equals(type)) {
            V3Support.onlyKeys(body, "postType", "title", "content", "tags", "routeSummary", "routeNodes");
            title = V3Support.requiredText(body, "title", 1, 80);
            routeSummary = V3Support.requiredText(body, "routeSummary", 1, 300);
            List<Map<String, Object>> requested = V3Support.objectList(body, "routeNodes", 1, 12);
            routeNodes = new ArrayList<>();
            for (int index = 0; index < requested.size(); index++) {
                Map<String, Object> node = requested.get(index);
                V3Support.onlyKeys(node, "sequence", "placeId", "note");
                int sequence = V3Support.positiveInt(node, "sequence");
                if (sequence != index + 1) {
                    V3Support.bad("routeNodes.sequence 必须与数组顺序连续一致");
                }
                String placeId = V3Support.uuid(node, "placeId");
                Map<String, Object> place = jdbc.queryForList(
                        "SELECT name FROM place WHERE id=? AND catalog_status='PUBLISHED'", placeId)
                        .stream().findFirst().orElse(null);
                if (place == null) {
                    V3Support.notFound();
                }
                routeNodes.add(V3Support.map("sequence", sequence, "placeId", placeId,
                        "placeName", place.get("name"), "note", V3Support.optionalText(node, "note", 300)));
            }
        } else {
            V3Support.bad("postType 无效");
            return null;
        }
        String content = V3Support.requiredText(body, "content", 1, 2000);
        List<String> tags = V3Support.stringList(body, "tags", 5, 20);
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO community_post(
                  id,visitor_id,account_id,title,content,author_name,cover_url,tags,post_type,
                  route_summary,route_nodes_json,demo_data,legacy_data,published)
                VALUES (?,NULL,?,?,?,?,NULL,?,?,?,?,true,false,true)
                """, id, principal.accountId(), title, content, principal.nickname(), String.join(",", tags), type,
                routeSummary, json(routeNodes));
        return get(id);
    }

    private Map<String, Object> view(Map<String, Object> row) {
        boolean legacy = row.get("account_id") == null || bool(row.get("legacy_data"));
        List<Map<String, Object>> nodes = readNodes(row.get("route_nodes_json"));
        List<Map<String, Object>> views = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            String placeId = node.get("placeId") instanceof String value ? value : null;
            boolean drawable = false;
            if (placeId != null) {
                Integer count = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM place WHERE id=? AND catalog_status='PUBLISHED'
                          AND schematic_x IS NOT NULL AND schematic_y IS NOT NULL
                        """, Integer.class, placeId);
                drawable = count != null && count == 1;
            }
            views.add(V3Support.map("sequence", node.get("sequence"), "placeId", placeId,
                    "placeName", node.get("placeName"), "note", node.get("note"), "drawable", drawable));
        }
        return V3Support.map("id", row.get("id"), "postType", row.get("post_type"), "title", row.get("title"),
                "content", row.get("content"), "authorName", row.get("author_name"), "coverUrl", null,
                "tags", V3Support.tags(row.get("tags")), "routeSummary", row.get("route_summary"),
                "routeNodes", views, "demoData", bool(row.get("demo_data")), "legacyData", legacy,
                "publishedAt", V3Support.utc(row.get("published_at")));
    }

    private List<Map<String, Object>> readNodes(Object raw) {
        if (raw == null || raw.toString().isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw.toString(), new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("社区路线无法序列化", exception);
        }
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : value != null && ((Number) value).intValue() != 0;
    }
}
