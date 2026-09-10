package com.guizhou.wudong.repository;

import com.guizhou.wudong.domain.FoodOrder;
import com.guizhou.wudong.domain.FoodOrderStatus;
import com.guizhou.wudong.domain.ProductOrder;
import com.guizhou.wudong.domain.ProductOrderStatus;
import com.guizhou.wudong.domain.StayBooking;
import com.guizhou.wudong.domain.StayBookingStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {
    private static final String PRODUCT_ORDER_SELECT = """
            SELECT o.*, p.name AS product_name
            FROM product_order o JOIN product p ON p.id = o.product_id
            """;
    private static final String FOOD_ORDER_SELECT = """
            SELECT o.*, f.name AS food_item_name
            FROM food_order o JOIN food_item f ON f.id = o.food_item_id
            """;
    private static final String STAY_ORDER_SELECT = """
            SELECT o.*, r.name AS room_type_name
            FROM stay_booking o JOIN room_type r ON r.id = o.room_type_id
            """;

    private static final RowMapper<ProductOrder> PRODUCT_ORDER_MAPPER = (rs, row) -> new ProductOrder(
            rs.getString("id"), rs.getString("visitor_id"), rs.getString("product_id"),
            rs.getString("product_name"), rs.getInt("quantity"), rs.getString("pickup_point"),
            rs.getString("contact_name"), rs.getString("contact_phone"), rs.getString("note"),
            ProductOrderStatus.valueOf(rs.getString("status")), rs.getString("source_thread_id"),
            rs.getBoolean("demo_data"), localDateTime(rs.getTimestamp("created_at")),
            localDateTime(rs.getTimestamp("updated_at")));
    private static final RowMapper<FoodOrder> FOOD_ORDER_MAPPER = (rs, row) -> new FoodOrder(
            rs.getString("id"), rs.getString("visitor_id"), rs.getString("food_item_id"),
            rs.getString("food_item_name"), localDateTime(rs.getTimestamp("visit_at")),
            rs.getInt("people_count"), rs.getString("contact_name"), rs.getString("contact_phone"),
            rs.getString("note"), FoodOrderStatus.valueOf(rs.getString("status")),
            rs.getString("source_thread_id"), rs.getBoolean("demo_data"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")));
    private static final RowMapper<StayBooking> STAY_ORDER_MAPPER = (rs, row) -> new StayBooking(
            rs.getString("id"), rs.getString("visitor_id"), rs.getString("room_type_id"),
            rs.getString("room_type_name"), rs.getDate("check_in_date").toLocalDate(),
            rs.getInt("people_count"), rs.getString("contact_name"), rs.getString("contact_phone"),
            rs.getString("note"), StayBookingStatus.valueOf(rs.getString("status")),
            rs.getString("source_thread_id"), rs.getBoolean("demo_data"),
            localDateTime(rs.getTimestamp("created_at")), localDateTime(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ProductCatalog(
            String id,
            String name,
            String pickupPoint,
            String catalogStatus,
            String merchantStatus
    ) {
    }

    public record FoodCatalog(String id, String name, String catalogStatus, String merchantStatus) {
    }

    public record RoomCatalog(
            String id,
            String name,
            int maxGuests,
            String catalogStatus,
            String stayStatus,
            String merchantStatus
    ) {
    }

    public Optional<ProductCatalog> findProductCatalog(String id) {
        return jdbc.query("""
                        SELECT p.id, p.name, p.pickup_point, p.catalog_status,
                               m.catalog_status AS merchant_status
                        FROM product p JOIN merchant m ON m.id = p.merchant_id
                        WHERE p.id = ?
                        """,
                (rs, row) -> new ProductCatalog(rs.getString("id"), rs.getString("name"),
                        rs.getString("pickup_point"), rs.getString("catalog_status"),
                        rs.getString("merchant_status")), id).stream().findFirst();
    }

    public Optional<FoodCatalog> findFoodCatalog(String id) {
        return jdbc.query("""
                        SELECT f.id, f.name, f.catalog_status, m.catalog_status AS merchant_status
                        FROM food_item f JOIN merchant m ON m.id = f.merchant_id
                        WHERE f.id = ?
                        """,
                (rs, row) -> new FoodCatalog(rs.getString("id"), rs.getString("name"),
                        rs.getString("catalog_status"), rs.getString("merchant_status")), id)
                .stream().findFirst();
    }

    public Optional<RoomCatalog> findRoomCatalog(String id) {
        return jdbc.query("""
                        SELECT r.id, r.name, r.max_guests, r.catalog_status,
                               s.catalog_status AS stay_status, m.catalog_status AS merchant_status
                        FROM room_type r
                        JOIN stay_property s ON s.id = r.stay_property_id
                        JOIN merchant m ON m.id = s.merchant_id
                        WHERE r.id = ?
                        """,
                (rs, row) -> new RoomCatalog(rs.getString("id"), rs.getString("name"),
                        rs.getInt("max_guests"), rs.getString("catalog_status"),
                        rs.getString("stay_status"), rs.getString("merchant_status")), id)
                .stream().findFirst();
    }

    public void insertProductOrder(
            String id,
            String visitorId,
            String productId,
            int quantity,
            String pickupPoint,
            String contactName,
            String contactPhone,
            String note,
            String status,
            String sourceThreadId) {
        jdbc.update("""
                        INSERT INTO product_order(
                            id, visitor_id, product_id, quantity, pickup_point, contact_name, contact_phone,
                            note, status, source_thread_id, demo_data)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                        """,
                id, visitorId, productId, quantity, pickupPoint, contactName, contactPhone, note, status,
                sourceThreadId);
    }

    public void insertFoodOrder(
            String id,
            String visitorId,
            String foodItemId,
            LocalDateTime visitAt,
            int peopleCount,
            String contactName,
            String contactPhone,
            String note,
            String status,
            String sourceThreadId) {
        jdbc.update("""
                        INSERT INTO food_order(
                            id, visitor_id, food_item_id, visit_at, people_count, contact_name, contact_phone,
                            note, status, source_thread_id, demo_data)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                        """,
                id, visitorId, foodItemId, Timestamp.valueOf(visitAt), peopleCount, contactName, contactPhone, note,
                status, sourceThreadId);
    }

    public void insertStayBooking(
            String id,
            String visitorId,
            String roomTypeId,
            LocalDate checkInDate,
            int peopleCount,
            String contactName,
            String contactPhone,
            String note,
            String status,
            String sourceThreadId) {
        jdbc.update("""
                        INSERT INTO stay_booking(
                            id, visitor_id, room_type_id, check_in_date, people_count, contact_name, contact_phone,
                            note, status, source_thread_id, demo_data)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)
                        """,
                id, visitorId, roomTypeId, Date.valueOf(checkInDate), peopleCount, contactName, contactPhone, note,
                status, sourceThreadId);
    }

    public Optional<ProductOrder> findProductOrder(String id) {
        return jdbc.query(PRODUCT_ORDER_SELECT + " WHERE o.id = ?", PRODUCT_ORDER_MAPPER, id).stream().findFirst();
    }

    public Optional<FoodOrder> findFoodOrder(String id) {
        return jdbc.query(FOOD_ORDER_SELECT + " WHERE o.id = ?", FOOD_ORDER_MAPPER, id).stream().findFirst();
    }

    public Optional<StayBooking> findStayBooking(String id) {
        return jdbc.query(STAY_ORDER_SELECT + " WHERE o.id = ?", STAY_ORDER_MAPPER, id).stream().findFirst();
    }

    public List<ProductOrder> findProductOrdersByVisitor(String visitorId) {
        return jdbc.query(PRODUCT_ORDER_SELECT + " WHERE o.visitor_id = ? ORDER BY o.created_at DESC",
                PRODUCT_ORDER_MAPPER, visitorId);
    }

    public List<FoodOrder> findFoodOrdersByVisitor(String visitorId) {
        return jdbc.query(FOOD_ORDER_SELECT + " WHERE o.visitor_id = ? ORDER BY o.created_at DESC",
                FOOD_ORDER_MAPPER, visitorId);
    }

    public List<StayBooking> findStayBookingsByVisitor(String visitorId) {
        return jdbc.query(STAY_ORDER_SELECT + " WHERE o.visitor_id = ? ORDER BY o.created_at DESC",
                STAY_ORDER_MAPPER, visitorId);
    }

    public List<ProductOrder> findProductOrders(String status) {
        if (status == null) {
            return jdbc.query(PRODUCT_ORDER_SELECT + " ORDER BY o.created_at DESC", PRODUCT_ORDER_MAPPER);
        }
        return jdbc.query(PRODUCT_ORDER_SELECT + " WHERE o.status = ? ORDER BY o.created_at DESC",
                PRODUCT_ORDER_MAPPER, status);
    }

    public List<FoodOrder> findFoodOrders(String status) {
        if (status == null) {
            return jdbc.query(FOOD_ORDER_SELECT + " ORDER BY o.created_at DESC", FOOD_ORDER_MAPPER);
        }
        return jdbc.query(FOOD_ORDER_SELECT + " WHERE o.status = ? ORDER BY o.created_at DESC",
                FOOD_ORDER_MAPPER, status);
    }

    public List<StayBooking> findStayBookings(String status) {
        if (status == null) {
            return jdbc.query(STAY_ORDER_SELECT + " ORDER BY o.created_at DESC", STAY_ORDER_MAPPER);
        }
        return jdbc.query(STAY_ORDER_SELECT + " WHERE o.status = ? ORDER BY o.created_at DESC",
                STAY_ORDER_MAPPER, status);
    }

    public int updateProductStatus(String id, String currentStatus, String targetStatus) {
        return jdbc.update("UPDATE product_order SET status = ? WHERE id = ? AND status = ?",
                targetStatus, id, currentStatus);
    }

    public int updateFoodStatus(String id, String currentStatus, String targetStatus) {
        return jdbc.update("UPDATE food_order SET status = ? WHERE id = ? AND status = ?",
                targetStatus, id, currentStatus);
    }

    public int updateStayStatus(String id, String currentStatus, String targetStatus) {
        return jdbc.update("UPDATE stay_booking SET status = ? WHERE id = ? AND status = ?",
                targetStatus, id, currentStatus);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
