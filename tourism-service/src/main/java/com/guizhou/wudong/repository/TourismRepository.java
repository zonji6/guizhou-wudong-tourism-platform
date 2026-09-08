package com.guizhou.wudong.repository;

import com.guizhou.wudong.api.NotFoundException;
import com.guizhou.wudong.domain.Booking;
import com.guizhou.wudong.domain.CommunityPost;
import com.guizhou.wudong.domain.KnowledgeDocument;
import com.guizhou.wudong.domain.ServiceResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class TourismRepository {
    private final JdbcTemplate jdbc;

    public TourismRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ServiceResource> findServices(String category, String keyword, String tags) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.merchant_id, m.name merchant_name, s.name, s.category, s.description,
                       s.price, s.duration_text, s.location_text, s.tags, s.image_url, s.demo_data
                FROM service_resource s JOIN merchant m ON m.id = s.merchant_id WHERE s.active = true
                """);
        List<Object> parameters = new ArrayList<>();
        if (hasText(category)) {
            sql.append(" AND s.category = ?");
            parameters.add(category);
        }
        if (hasText(keyword)) {
            sql.append(" AND CONCAT(s.name, ' ', s.description, ' ', COALESCE(s.tags, '')) LIKE ?");
            parameters.add("%" + keyword.trim() + "%");
        }
        if (hasText(tags)) {
            sql.append(" AND s.tags LIKE ?");
            parameters.add("%" + tags.trim() + "%");
        }
        sql.append(" ORDER BY s.category, s.price");
        return jdbc.query(sql.toString(), (rs, row) -> new ServiceResource(
                rs.getString("id"), rs.getString("merchant_id"), rs.getString("merchant_name"),
                rs.getString("name"), rs.getString("category"), rs.getString("description"),
                rs.getBigDecimal("price"), rs.getString("duration_text"), rs.getString("location_text"),
                rs.getString("tags"), rs.getString("image_url"), rs.getBoolean("demo_data")), parameters.toArray());
    }

    public ServiceResource findService(String id) {
        return findServices(null, null, null).stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("未找到该演示服务"));
    }

    public List<CommunityPost> findPosts() {
        return jdbc.query("SELECT * FROM community_post ORDER BY published_at DESC", (rs, row) -> new CommunityPost(
                rs.getString("id"), rs.getString("title"), rs.getString("content"), rs.getString("author_name"),
                rs.getString("cover_url"), rs.getString("tags"), rs.getBoolean("demo_data"), rs.getTimestamp("published_at").toLocalDateTime()));
    }

    public List<KnowledgeDocument> findKnowledge(String keyword) {
        String sql = "SELECT * FROM knowledge_document WHERE published = true";
        List<Object> parameters = new ArrayList<>();
        if (hasText(keyword)) {
            sql += " AND CONCAT(title, ' ', content, ' ', COALESCE(tags, '')) LIKE ?";
            parameters.add("%" + keyword.trim() + "%");
        }
        sql += " ORDER BY updated_at DESC";
        return jdbc.query(sql, (rs, row) -> new KnowledgeDocument(
                rs.getString("id"), rs.getString("title"), rs.getString("content"), rs.getString("source_type"),
                rs.getString("tags"), rs.getBoolean("published"), rs.getString("index_status"),
                rs.getBoolean("demo_data"), rs.getTimestamp("updated_at").toLocalDateTime()), parameters.toArray());
    }

    public void insertKnowledge(String id, String title, String content, String tags) {
        jdbc.update("INSERT INTO knowledge_document(id, title, content, source_type, tags, published, index_status, demo_data) VALUES (?, ?, ?, 'ADMIN', ?, true, 'PENDING', true)",
                id, title, content, tags);
    }

    public void insertBooking(String id, String serviceId, Date travelDate, int peopleCount, String contactName,
                              String contactPhone, String note, String status, String source, String threadId) {
        jdbc.update("INSERT INTO booking(id, service_id, travel_date, people_count, contact_name, contact_phone, note, status, source, thread_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, serviceId, travelDate, peopleCount, contactName, contactPhone, note, status, source, threadId);
    }

    public Booking findBooking(String id) {
        List<Booking> results = jdbc.query("""
                SELECT b.*, s.name service_name FROM booking b JOIN service_resource s ON s.id = b.service_id WHERE b.id = ?
                """, (rs, row) -> new Booking(rs.getString("id"), rs.getString("service_id"), rs.getString("service_name"),
                rs.getDate("travel_date").toLocalDate(), rs.getInt("people_count"), rs.getString("contact_name"),
                rs.getString("contact_phone"), rs.getString("note"), rs.getString("status"), rs.getString("source"),
                rs.getString("thread_id"), rs.getTimestamp("created_at").toLocalDateTime()), id);
        return results.stream().findFirst().orElseThrow(() -> new NotFoundException("未找到该预约"));
    }

    public void updateBookingStatus(String id, String status) {
        if (jdbc.update("UPDATE booking SET status = ? WHERE id = ?", status, id) == 0) {
            throw new NotFoundException("未找到该预约");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
