package com.guizhou.wudong.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.guizhou.wudong.api.CatalogRequests;
import com.guizhou.wudong.api.CatalogViews;
import com.guizhou.wudong.api.NotFoundException;
import com.guizhou.wudong.domain.CatalogStatus;
import com.guizhou.wudong.domain.MapStatus;
import com.guizhou.wudong.repository.CatalogRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CatalogService {
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    public List<CatalogViews.ProductView> publicProducts(String categoryTag, List<String> tags) {
        validateCategoryTag(categoryTag, Set.of("茶与伴手礼", "苗绣文创"));
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(productSelect())
                .append(" WHERE p.catalog_status = 'PUBLISHED' AND m.catalog_status = 'PUBLISHED'");
        appendTagFilters(sql, parameters, "p.tags", categoryTag, tags);
        return repository.query(sql.toString(), parameters.toArray()).stream().map(this::productView).toList();
    }

    public CatalogViews.ProductView publicProduct(String id) {
        return publishedOne(productSelect(), "p", id).map(this::productView)
                .orElseThrow(() -> new NotFoundException("未找到目录项"));
    }

    public List<CatalogViews.FoodView> publicFoods(String categoryTag, List<String> tags) {
        validateCategoryTag(categoryTag, Set.of("茶点", "正餐", "体验"));
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(foodSelect())
                .append(" WHERE f.catalog_status = 'PUBLISHED' AND m.catalog_status = 'PUBLISHED'");
        appendTagFilters(sql, parameters, "f.tags", categoryTag, tags);
        return repository.query(sql.toString(), parameters.toArray()).stream().map(this::foodView).toList();
    }

    public CatalogViews.FoodView publicFood(String id) {
        return publishedOne(foodSelect(), "f", id).map(this::foodView)
                .orElseThrow(() -> new NotFoundException("未找到目录项"));
    }

    public List<CatalogViews.StayView> publicStays(Integer peopleCount, String roomTypeId, List<String> tags) {
        if (peopleCount != null && peopleCount <= 0) {
            throw new IllegalArgumentException("人数必须为正数");
        }
        String roomId = roomTypeId == null ? null : uuid(roomTypeId);
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(staySelect())
                .append(" WHERE s.catalog_status = 'PUBLISHED' AND m.catalog_status = 'PUBLISHED'");
        appendTagFilters(sql, parameters, "s.tags", null, tags);
        if (peopleCount != null || roomId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM room_type r WHERE r.stay_property_id = s.id")
                    .append(" AND r.catalog_status = 'PUBLISHED'");
            if (peopleCount != null) {
                sql.append(" AND r.max_guests >= ?");
                parameters.add(peopleCount);
            }
            if (roomId != null) {
                sql.append(" AND r.id = ?");
                parameters.add(roomId);
            }
            sql.append(")");
        }
        return repository.query(sql.toString(), parameters.toArray()).stream()
                .map(row -> stayView(row, roomsForStay(string(row, "id"), true, peopleCount, roomId))).toList();
    }

    public CatalogViews.StayView publicStay(String id) {
        Map<String, Object> row = publishedOne(staySelect(), "s", id)
                .orElseThrow(() -> new NotFoundException("未找到目录项"));
        return stayView(row, roomsForStay(string(row, "id"), true, null, null));
    }

    public CatalogViews.RoomTypeView publicRoomType(String id) {
        String normalizedId = uuid(id);
        return repository.queryOne(roomSelect() + " WHERE r.id = ? AND r.catalog_status = 'PUBLISHED'"
                        + " AND s.catalog_status = 'PUBLISHED' AND m.catalog_status = 'PUBLISHED'", normalizedId)
                .map(this::roomView)
                .orElseThrow(() -> new NotFoundException("未找到目录项"));
    }

    public CatalogViews.MapPlacesView publicPlaces(String category, List<String> tags) {
        if (category != null && category.isBlank()) {
            throw new IllegalArgumentException("地点类别筛选无效");
        }
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null || tag.isBlank() || tag.contains(",")) {
                    throw new IllegalArgumentException("标签筛选无效");
                }
            }
        }
        return new CatalogViews.MapPlacesView(MapStatus.NOT_CONFIGURED, List.of(),
                "地图服务或已核验坐标尚未配置");
    }

    public List<?> adminList(CatalogRequests.Kind kind) {
        return repository.query(adminSelect(kind) + " ORDER BY " + idColumn(kind).replace(".id", ".created_at") + " DESC").stream()
                .map(row -> adminView(kind, row)).toList();
    }

    public Object adminDetail(CatalogRequests.Kind kind, String id) {
        return adminRow(kind, id).map(row -> adminView(kind, row))
                .orElseThrow(() -> new NotFoundException("未找到目录项"));
    }

    public Object create(CatalogRequests.Kind kind, JsonNode body) {
        validateCreate(kind, body);
        String id = UUID.randomUUID().toString();
        switch (kind) {
            case MERCHANT -> repository.update(
                    "INSERT INTO merchant(id, name, description, contact_phone, demo_data, catalog_status) VALUES (?, ?, ?, ?, true, 'UNPUBLISHED')",
                    id, requiredText(body, "name"), requiredText(body, "description", 500),
                    nullableText(body, "contactPhone"));
            case PRODUCT -> {
                String merchantId = uuid(requiredText(body, "merchantId"));
                adminDetail(CatalogRequests.Kind.MERCHANT, merchantId);
                repository.update("INSERT INTO product(id, merchant_id, name, description, price, pickup_point, tags, image_url, demo_data, catalog_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, 'UNPUBLISHED')",
                        id, merchantId, requiredText(body, "name"), requiredText(body, "description"), requiredNumber(body, "price"),
                        requiredText(body, "pickupPoint"), tags(body, "tags"), nullableText(body, "imageUrl"));
            }
            case FOOD -> {
                String merchantId = uuid(requiredText(body, "merchantId"));
                adminDetail(CatalogRequests.Kind.MERCHANT, merchantId);
                repository.update("INSERT INTO food_item(id, merchant_id, name, description, price, visit_time_text, tags, image_url, demo_data, catalog_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, 'UNPUBLISHED')",
                        id, merchantId, requiredText(body, "name"), requiredText(body, "description"), requiredNumber(body, "price"),
                        nullableText(body, "visitTimeText"), tags(body, "tags"), nullableText(body, "imageUrl"));
            }
            case STAY -> {
                String merchantId = uuid(requiredText(body, "merchantId"));
                adminDetail(CatalogRequests.Kind.MERCHANT, merchantId);
                repository.update("INSERT INTO stay_property(id, merchant_id, name, description, location_text, tags, image_url, demo_data, catalog_status) VALUES (?, ?, ?, ?, ?, ?, ?, true, 'UNPUBLISHED')",
                        id, merchantId, requiredText(body, "name"), requiredText(body, "description"),
                        nullableText(body, "locationText"), tags(body, "tags"), nullableText(body, "imageUrl"));
            }
            case ROOM_TYPE -> {
                String stayId = uuid(requiredText(body, "stayPropertyId"));
                adminDetail(CatalogRequests.Kind.STAY, stayId);
                repository.update("INSERT INTO room_type(id, stay_property_id, name, description, max_guests, price, image_url, demo_data, catalog_status) VALUES (?, ?, ?, ?, ?, ?, ?, true, 'UNPUBLISHED')",
                        id, stayId, requiredText(body, "name"), requiredText(body, "description"), requiredInteger(body, "maxGuests"),
                        requiredNumber(body, "price"), nullableText(body, "imageUrl"));
            }
            case PLACE -> {
                coordinatePair(body, true);
                repository.update("INSERT INTO place(id, name, category, description, latitude, longitude, tags, image_url, demo_data, catalog_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, true, 'UNPUBLISHED')",
                        id, requiredText(body, "name"), requiredText(body, "category"), requiredText(body, "description"),
                        nullableNumber(body, "latitude"), nullableNumber(body, "longitude"), tags(body, "tags"), nullableText(body, "imageUrl"));
            }
        }
        return adminDetail(kind, id);
    }

    public Object patch(CatalogRequests.Kind kind, String id, JsonNode body) {
        validatePatch(kind, body);
        Map<String, Object> changes = new LinkedHashMap<>();
        switch (kind) {
            case MERCHANT -> {
                putText(body, changes, "name", "name", false);
                putText(body, changes, "description", "description", false, 500);
                putText(body, changes, "contactPhone", "contact_phone", true);
            }
            case PRODUCT -> {
                putText(body, changes, "name", "name", false);
                putText(body, changes, "description", "description", false);
                putNumber(body, changes, "price", "price", false);
                putText(body, changes, "pickupPoint", "pickup_point", false);
                putTags(body, changes);
                putText(body, changes, "imageUrl", "image_url", true);
            }
            case FOOD -> {
                putText(body, changes, "name", "name", false);
                putText(body, changes, "description", "description", false);
                putNumber(body, changes, "price", "price", false);
                putText(body, changes, "visitTimeText", "visit_time_text", true);
                putTags(body, changes);
                putText(body, changes, "imageUrl", "image_url", true);
            }
            case STAY -> {
                putText(body, changes, "name", "name", false);
                putText(body, changes, "description", "description", false);
                putText(body, changes, "locationText", "location_text", true);
                putTags(body, changes);
                putText(body, changes, "imageUrl", "image_url", true);
            }
            case ROOM_TYPE -> {
                putText(body, changes, "name", "name", false);
                putText(body, changes, "description", "description", false);
                putInteger(body, changes, "maxGuests", "max_guests");
                putNumber(body, changes, "price", "price", false);
                putText(body, changes, "imageUrl", "image_url", true);
            }
            case PLACE -> {
                coordinatePair(body, false);
                putText(body, changes, "name", "name", false);
                putText(body, changes, "category", "category", false);
                putText(body, changes, "description", "description", false);
                putCoordinate(body, changes, "latitude", "latitude");
                putCoordinate(body, changes, "longitude", "longitude");
                putTags(body, changes);
                putText(body, changes, "imageUrl", "image_url", true);
            }
        }
        updateColumns(kind, id, changes);
        return adminDetail(kind, id);
    }

    public Object catalogStatus(CatalogRequests.Kind kind, String id, JsonNode body) {
        rejectUnknown(body, Set.of("catalogStatus"));
        if (body.size() != 1 || !body.hasNonNull("catalogStatus") || !body.get("catalogStatus").isTextual()) {
            throw new IllegalArgumentException("状态请求无效");
        }
        CatalogStatus status;
        try {
            status = CatalogStatus.valueOf(body.get("catalogStatus").textValue());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("状态值无效");
        }
        updateColumns(kind, id, Map.of("catalog_status", status.name()));
        return adminDetail(kind, id);
    }

    private Optional<Map<String, Object>> publishedOne(String select, String alias, String id) {
        String normalizedId = uuid(id);
        return repository.queryOne(select + " WHERE " + alias + ".id = ? AND " + alias
                + ".catalog_status = 'PUBLISHED' AND m.catalog_status = 'PUBLISHED'", normalizedId);
    }

    private List<CatalogViews.RoomTypeView> roomsForStay(String stayId, boolean published, Integer peopleCount, String roomTypeId) {
        List<Object> parameters = new ArrayList<>();
        StringBuilder sql = new StringBuilder(roomSelect()).append(" WHERE r.stay_property_id = ?");
        parameters.add(stayId);
        if (published) {
            sql.append(" AND r.catalog_status = 'PUBLISHED' AND s.catalog_status = 'PUBLISHED' AND m.catalog_status = 'PUBLISHED'");
        }
        if (peopleCount != null) {
            sql.append(" AND r.max_guests >= ?");
            parameters.add(peopleCount);
        }
        if (roomTypeId != null) {
            sql.append(" AND r.id = ?");
            parameters.add(roomTypeId);
        }
        return repository.query(sql.toString(), parameters.toArray()).stream().map(this::roomView).toList();
    }

    private Optional<Map<String, Object>> adminRow(CatalogRequests.Kind kind, String id) {
        String normalizedId = uuid(id);
        return repository.queryOne(adminSelect(kind) + " WHERE " + idColumn(kind) + " = ?", normalizedId);
    }

    private Object adminView(CatalogRequests.Kind kind, Map<String, Object> row) {
        return switch (kind) {
            case MERCHANT -> merchantView(row);
            case PRODUCT -> productView(row);
            case FOOD -> foodView(row);
            case STAY -> stayView(row, roomsForStay(string(row, "id"), false, null, null));
            case ROOM_TYPE -> roomView(row);
            case PLACE -> placeView(row);
        };
    }

    private CatalogViews.MerchantView merchantView(Map<String, Object> row) {
        return new CatalogViews.MerchantView(string(row, "id"), string(row, "name"), string(row, "description"),
                string(row, "contact_phone"), bool(row, "demo_data"), status(row), dateTime(row, "created_at"));
    }

    private CatalogViews.ProductView productView(Map<String, Object> row) {
        return new CatalogViews.ProductView(string(row, "id"), string(row, "merchant_id"), string(row, "merchant_name"),
                string(row, "name"), string(row, "description"), number(row, "price"), string(row, "pickup_point"),
                tags(string(row, "tags")), string(row, "image_url"), bool(row, "demo_data"), status(row));
    }

    private CatalogViews.FoodView foodView(Map<String, Object> row) {
        return new CatalogViews.FoodView(string(row, "id"), string(row, "merchant_id"), string(row, "merchant_name"),
                string(row, "name"), string(row, "description"), number(row, "price"), string(row, "visit_time_text"),
                tags(string(row, "tags")), string(row, "image_url"), bool(row, "demo_data"), status(row));
    }

    private CatalogViews.StayView stayView(Map<String, Object> row, List<CatalogViews.RoomTypeView> roomTypes) {
        return new CatalogViews.StayView(string(row, "id"), string(row, "merchant_id"), string(row, "merchant_name"),
                string(row, "name"), string(row, "description"), string(row, "location_text"), tags(string(row, "tags")),
                string(row, "image_url"), bool(row, "demo_data"), status(row), roomTypes);
    }

    private CatalogViews.RoomTypeView roomView(Map<String, Object> row) {
        return new CatalogViews.RoomTypeView(string(row, "id"), string(row, "stay_property_id"),
                string(row, "stay_property_name"), string(row, "name"), string(row, "description"),
                integer(row, "max_guests"), number(row, "price"), string(row, "image_url"), bool(row, "demo_data"),
                status(row));
    }

    private CatalogViews.PlaceView placeView(Map<String, Object> row) {
        return new CatalogViews.PlaceView(string(row, "id"), string(row, "name"), string(row, "category"),
                string(row, "description"), nullableNumber(row, "latitude"), nullableNumber(row, "longitude"),
                tags(string(row, "tags")), string(row, "image_url"), bool(row, "demo_data"), status(row));
    }

    private void validateCreate(CatalogRequests.Kind kind, JsonNode body) {
        rejectUnknown(body, createFields(kind));
        for (String field : requiredFields(kind)) {
            if (!body.hasNonNull(field)) {
                throw new IllegalArgumentException("缺少必填字段");
            }
        }
        rejectUnexpectedNulls(kind, body, true);
    }

    private void validatePatch(CatalogRequests.Kind kind, JsonNode body) {
        rejectUnknown(body, patchFields(kind));
        if (body.size() == 0) {
            throw new IllegalArgumentException("PATCH 至少包含一个字段");
        }
        rejectUnexpectedNulls(kind, body, false);
    }

    private void rejectUnexpectedNulls(CatalogRequests.Kind kind, JsonNode body, boolean create) {
        body.fieldNames().forEachRemaining(field -> {
            if (body.get(field).isNull() && !nullableFields(kind).contains(field)) {
                throw new IllegalArgumentException("字段不允许为 null");
            }
        });
        if (kind == CatalogRequests.Kind.PLACE) {
            coordinatePair(body, create);
        }
    }

    private void coordinatePair(JsonNode body, boolean create) {
        boolean hasLatitude = body.has("latitude");
        boolean hasLongitude = body.has("longitude");
        if (hasLatitude != hasLongitude) {
            throw new IllegalArgumentException("经纬度必须同时提供");
        }
        if (hasLatitude) {
            JsonNode latitude = body.get("latitude");
            JsonNode longitude = body.get("longitude");
            if (latitude.isNull() != longitude.isNull()) {
                throw new IllegalArgumentException("经纬度必须同时为 null");
            }
            if (!latitude.isNull()) {
                coordinate(latitude, true);
                coordinate(longitude, false);
            }
        } else if (!create && (hasLatitude || hasLongitude)) {
            throw new IllegalArgumentException("经纬度必须同时提供");
        }
    }

    private void rejectUnknown(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("请求体必须是对象");
        }
        body.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("存在不允许的字段");
            }
        });
    }

    private void updateColumns(CatalogRequests.Kind kind, String id, Map<String, Object> changes) {
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("PATCH 至少包含一个字段");
        }
        String normalizedId = uuid(id);
        StringBuilder sql = new StringBuilder("UPDATE ").append(table(kind)).append(" SET ");
        List<Object> parameters = new ArrayList<>();
        for (String column : changes.keySet()) {
            sql.append(column).append(" = ?, ");
            parameters.add(changes.get(column));
        }
        sql.setLength(sql.length() - 2);
        sql.append(" WHERE id = ?");
        parameters.add(normalizedId);
        if (repository.update(sql.toString(), parameters.toArray()) == 0) {
            throw new NotFoundException("未找到目录项");
        }
    }

    private void putText(JsonNode body, Map<String, Object> changes, String field, String column, boolean nullable) {
        putText(body, changes, field, column, nullable, maxLength(field));
    }

    private void putText(JsonNode body, Map<String, Object> changes, String field, String column, boolean nullable,
                         int maxLength) {
        if (!body.has(field)) {
            return;
        }
        if (body.get(field).isNull()) {
            if (!nullable) {
                throw new IllegalArgumentException("字段不允许为 null");
            }
            changes.put(column, null);
            return;
        }
        changes.put(column, text(body.get(field), maxLength));
    }

    private void putNumber(JsonNode body, Map<String, Object> changes, String field, String column, boolean nullable) {
        if (!body.has(field)) {
            return;
        }
        if (body.get(field).isNull()) {
            if (!nullable) {
                throw new IllegalArgumentException("字段不允许为 null");
            }
            changes.put(column, null);
            return;
        }
        changes.put(column, positiveNumber(body.get(field)));
    }

    private void putInteger(JsonNode body, Map<String, Object> changes, String field, String column) {
        if (body.has(field)) {
            changes.put(column, positiveInteger(body.get(field)));
        }
    }

    private void putCoordinate(JsonNode body, Map<String, Object> changes, String field, String column) {
        if (!body.has(field)) {
            return;
        }
        JsonNode node = body.get(field);
        if (node.isNull()) {
            changes.put(column, null);
            return;
        }
        changes.put(column, coordinate(node, "latitude".equals(field)));
    }

    private void putTags(JsonNode body, Map<String, Object> changes) {
        if (body.has("tags")) {
            changes.put("tags", tags(body, "tags"));
        }
    }

    private String requiredText(JsonNode body, String field) {
        return requiredText(body, field, maxLength(field));
    }

    private String requiredText(JsonNode body, String field, int maxLength) {
        if (!body.hasNonNull(field)) {
            throw new IllegalArgumentException("缺少必填字段");
        }
        return text(body.get(field), maxLength);
    }

    private String nullableText(JsonNode body, String field) {
        if (!body.has(field) || body.get(field).isNull()) {
            return null;
        }
        return text(body.get(field), maxLength(field));
    }

    private BigDecimal requiredNumber(JsonNode body, String field) {
        if (!body.hasNonNull(field)) {
            throw new IllegalArgumentException("缺少必填字段");
        }
        return positiveNumber(body.get(field));
    }

    private Integer requiredInteger(JsonNode body, String field) {
        if (!body.hasNonNull(field)) {
            throw new IllegalArgumentException("缺少必填字段");
        }
        return positiveInteger(body.get(field));
    }

    private BigDecimal nullableNumber(JsonNode body, String field) {
        if (!body.has(field) || body.get(field).isNull()) {
            return null;
        }
        return coordinate(body.get(field), "latitude".equals(field));
    }

    private String tags(JsonNode body, String field) {
        if (!body.has(field)) {
            return null;
        }
        JsonNode node = body.get(field);
        if (!node.isArray()) {
            throw new IllegalArgumentException("标签必须是数组");
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = text(item, 80);
            if (value.contains(",")) {
                throw new IllegalArgumentException("标签不能包含逗号");
            }
            values.add(value);
        }
        String csv = values.isEmpty() ? null : String.join(",", values);
        if (csv != null && csv.length() > 500) {
            throw new IllegalArgumentException("标签总长度不能超过 500 个字符");
        }
        return csv;
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

    private static BigDecimal positiveNumber(JsonNode node) {
        if (node == null || !node.isNumber() || !Double.isFinite(node.doubleValue())
                || node.decimalValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("数字字段必须为正数");
        }
        BigDecimal value = node.decimalValue();
        if (value.scale() > 2 || value.compareTo(MAX_PRICE) > 0) {
            throw new IllegalArgumentException("金额最多保留两位小数且不能超过 99999999.99");
        }
        return value;
    }

    private static int positiveInteger(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() <= 0) {
            throw new IllegalArgumentException("整数必须为正数");
        }
        return node.intValue();
    }

    private static BigDecimal coordinate(JsonNode node, boolean latitude) {
        if (node == null || !node.isNumber() || !Double.isFinite(node.doubleValue())) {
            throw new IllegalArgumentException("经纬度必须为有限数值");
        }
        BigDecimal value = node.decimalValue();
        BigDecimal minimum = latitude ? MIN_LATITUDE : MIN_LONGITUDE;
        BigDecimal maximum = latitude ? MAX_LATITUDE : MAX_LONGITUDE;
        if (value.scale() > 7 || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("经纬度范围或精度无效");
        }
        return value;
    }

    private static String uuid(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("标识必须为 UUID");
        }
        try {
            String normalized = UUID.fromString(value).toString();
            if (!normalized.equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("标识必须为规范 UUID");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("标识必须为 UUID");
        }
    }

    private void appendTagFilters(StringBuilder sql, List<Object> parameters, String column,
                                  String categoryTag, List<String> tags) {
        if (categoryTag != null) {
            addTagFilter(sql, parameters, column, categoryTag);
        }
        if (tags != null) {
            for (String tag : tags) {
                addTagFilter(sql, parameters, column, tag);
            }
        }
    }

    private void addTagFilter(StringBuilder sql, List<Object> parameters, String column, String tag) {
        if (tag == null || tag.isBlank() || tag.contains(",")) {
            throw new IllegalArgumentException("标签筛选无效");
        }
        sql.append(" AND FIND_IN_SET(?, ").append(column).append(") > 0");
        parameters.add(tag.trim());
    }

    private static void validateCategoryTag(String categoryTag, Set<String> allowed) {
        if (categoryTag != null && !allowed.contains(categoryTag)) {
            throw new IllegalArgumentException("类别筛选无效");
        }
    }

    private static String productSelect() {
        return "SELECT p.*, m.name AS merchant_name FROM product p JOIN merchant m ON m.id = p.merchant_id";
    }

    private static String foodSelect() {
        return "SELECT f.*, m.name AS merchant_name FROM food_item f JOIN merchant m ON m.id = f.merchant_id";
    }

    private static String staySelect() {
        return "SELECT s.*, m.name AS merchant_name FROM stay_property s JOIN merchant m ON m.id = s.merchant_id";
    }

    private static String roomSelect() {
        return "SELECT r.*, s.name AS stay_property_name, m.name AS merchant_name FROM room_type r "
                + "JOIN stay_property s ON s.id = r.stay_property_id JOIN merchant m ON m.id = s.merchant_id";
    }

    private static String adminSelect(CatalogRequests.Kind kind) {
        return switch (kind) {
            case MERCHANT -> "SELECT * FROM merchant";
            case PRODUCT -> productSelect();
            case FOOD -> foodSelect();
            case STAY -> staySelect();
            case ROOM_TYPE -> roomSelect();
            case PLACE -> "SELECT * FROM place";
        };
    }

    private static String idColumn(CatalogRequests.Kind kind) {
        return switch (kind) {
            case MERCHANT -> "merchant.id";
            case PRODUCT -> "p.id";
            case FOOD -> "f.id";
            case STAY -> "s.id";
            case ROOM_TYPE -> "r.id";
            case PLACE -> "place.id";
        };
    }

    private static String table(CatalogRequests.Kind kind) {
        return switch (kind) {
            case MERCHANT -> "merchant";
            case PRODUCT -> "product";
            case FOOD -> "food_item";
            case STAY -> "stay_property";
            case ROOM_TYPE -> "room_type";
            case PLACE -> "place";
        };
    }

    private static Set<String> createFields(CatalogRequests.Kind kind) {
        return switch (kind) {
            case MERCHANT -> Set.of("name", "description", "contactPhone");
            case PRODUCT -> Set.of("merchantId", "name", "description", "price", "pickupPoint", "tags", "imageUrl");
            case FOOD -> Set.of("merchantId", "name", "description", "price", "visitTimeText", "tags", "imageUrl");
            case STAY -> Set.of("merchantId", "name", "description", "locationText", "tags", "imageUrl");
            case ROOM_TYPE -> Set.of("stayPropertyId", "name", "description", "maxGuests", "price", "imageUrl");
            case PLACE -> Set.of("name", "category", "description", "latitude", "longitude", "tags", "imageUrl");
        };
    }

    private static Set<String> patchFields(CatalogRequests.Kind kind) {
        return switch (kind) {
            case MERCHANT -> Set.of("name", "description", "contactPhone");
            case PRODUCT -> Set.of("name", "description", "price", "pickupPoint", "tags", "imageUrl");
            case FOOD -> Set.of("name", "description", "price", "visitTimeText", "tags", "imageUrl");
            case STAY -> Set.of("name", "description", "locationText", "tags", "imageUrl");
            case ROOM_TYPE -> Set.of("name", "description", "maxGuests", "price", "imageUrl");
            case PLACE -> Set.of("name", "category", "description", "latitude", "longitude", "tags", "imageUrl");
        };
    }

    private static Set<String> requiredFields(CatalogRequests.Kind kind) {
        return switch (kind) {
            case MERCHANT -> Set.of("name", "description");
            case PRODUCT -> Set.of("merchantId", "name", "description", "price", "pickupPoint");
            case FOOD -> Set.of("merchantId", "name", "description", "price");
            case STAY -> Set.of("merchantId", "name", "description");
            case ROOM_TYPE -> Set.of("stayPropertyId", "name", "description", "maxGuests", "price");
            case PLACE -> Set.of("name", "category", "description");
        };
    }

    private static Set<String> nullableFields(CatalogRequests.Kind kind) {
        return switch (kind) {
            case MERCHANT -> Set.of("contactPhone");
            case PRODUCT -> Set.of("imageUrl");
            case FOOD -> Set.of("visitTimeText", "imageUrl");
            case STAY -> Set.of("locationText", "imageUrl");
            case ROOM_TYPE -> Set.of("imageUrl");
            case PLACE -> Set.of("latitude", "longitude", "imageUrl");
        };
    }

    private static String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private static boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int integer(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    private static BigDecimal number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static BigDecimal nullableNumber(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : number(row, key);
    }

    private static CatalogStatus status(Map<String, Object> row) {
        return CatalogStatus.valueOf(string(row, "catalog_status"));
    }

    private static LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }

    private static List<String> tags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : csv.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return List.copyOf(values);
    }

    private static int maxLength(String field) {
        return switch (field) {
            case "name" -> 120;
            case "description" -> 1000;
            case "contactPhone" -> 32;
            case "pickupPoint", "visitTimeText", "locationText" -> 160;
            case "category" -> 64;
            case "imageUrl" -> 500;
            default -> 500;
        };
    }
}
