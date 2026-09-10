package com.guizhou.wudong.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guizhou.wudong.domain.PostType;
import com.guizhou.wudong.domain.RouteNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class CommunityRepository {
    private static final String PUBLIC_SELECT = """
            SELECT id, title, content, author_name, tags, post_type, route_summary,
                   route_nodes_json, demo_data, published_at
            FROM community_post
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public CommunityRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record StoredPost(
            String id,
            String title,
            String content,
            String authorName,
            String tags,
            PostType postType,
            String routeSummary,
            List<RouteNode> routeNodes,
            boolean demoData,
            LocalDateTime publishedAt
    ) {
    }

    public List<StoredPost> findPublished(PostType type, String tag) {
        StringBuilder sql = new StringBuilder(PUBLIC_SELECT).append(" WHERE published = true");
        List<Object> parameters = new ArrayList<>();
        if (type != null) {
            sql.append(" AND post_type = ?");
            parameters.add(type.name());
        }
        if (tag != null) {
            sql.append(" AND FIND_IN_SET(?, tags) > 0");
            parameters.add(tag);
        }
        sql.append(" ORDER BY published_at DESC, id DESC");
        return jdbc.query(sql.toString(), this::mapPost, parameters.toArray());
    }

    public Optional<StoredPost> findPublishedById(String id) {
        return jdbc.query(PUBLIC_SELECT + " WHERE id = ? AND published = true", this::mapPost, id)
                .stream().findFirst();
    }

    public Optional<String> findPlaceStatus(String id) {
        return jdbc.query("SELECT catalog_status FROM place WHERE id = ?",
                (rs, row) -> rs.getString("catalog_status"), id).stream().findFirst();
    }

    public void insert(
            String id,
            String visitorId,
            String title,
            String content,
            String authorName,
            String tags,
            PostType postType,
            String routeSummary,
            List<RouteNode> routeNodes) {
        jdbc.update("""
                        INSERT INTO community_post(
                            id, visitor_id, title, content, author_name, tags, post_type, route_summary,
                            route_nodes_json, demo_data, published)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true, true)
                        """,
                id, visitorId, title, content, authorName, tags, postType.name(), routeSummary,
                routeNodes == null ? null : routeNodesJson(routeNodes));
    }

    private StoredPost mapPost(ResultSet resultSet, int rowNumber) throws SQLException {
        return new StoredPost(
                resultSet.getString("id"),
                resultSet.getString("title"),
                resultSet.getString("content"),
                resultSet.getString("author_name"),
                resultSet.getString("tags"),
                PostType.valueOf(resultSet.getString("post_type")),
                resultSet.getString("route_summary"),
                routeNodes(resultSet.getString("route_nodes_json")),
                resultSet.getBoolean("demo_data"),
                localDateTime(resultSet.getTimestamp("published_at")));
    }

    private String routeNodesJson(List<RouteNode> routeNodes) {
        try {
            return objectMapper.writeValueAsString(routeNodes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线节点无法序列化", exception);
        }
    }

    private List<RouteNode> routeNodes(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return List.copyOf(objectMapper.readValue(json, new TypeReference<List<RouteNode>>() { }));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("路线节点数据无效", exception);
        }
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
