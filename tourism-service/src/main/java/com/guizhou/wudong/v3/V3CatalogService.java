package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class V3CatalogService {
    private static final String MAP_NOTICE = "水彩示意图，仅用于展示地点顺序，不提供实时导航、精确距离或预计时长。";
    private final JdbcTemplate jdbc;

    public V3CatalogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> products(String categoryTag, List<String> tags) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.*, m.name AS merchant_name, m.version AS merchant_version,
                       m.catalog_status AS merchant_catalog_status
                FROM product p JOIN merchant m ON m.id=p.merchant_id
                WHERE p.catalog_status='PUBLISHED' AND m.catalog_status='PUBLISHED'
                ORDER BY p.name, p.id
                """);
        return rows.stream().filter(row -> matchesTags(row.get("tags"), categoryTag, tags))
                .map(this::productView).toList();
    }

    public Map<String, Object> product(String id) {
        V3Support.uuid(id, "id");
        return publicOne("""
                SELECT p.*, m.name AS merchant_name, m.version AS merchant_version,
                       m.catalog_status AS merchant_catalog_status
                FROM product p JOIN merchant m ON m.id=p.merchant_id
                WHERE p.id=? AND p.catalog_status='PUBLISHED' AND m.catalog_status='PUBLISHED'
                """, id, this::productView);
    }

    public List<Map<String, Object>> foodMerchants(List<String> tags) {
        return jdbc.queryForList("""
                SELECT m.* FROM merchant m
                WHERE m.catalog_status='PUBLISHED' AND EXISTS (
                  SELECT 1 FROM food_item f WHERE f.merchant_id=m.id AND f.catalog_status='PUBLISHED')
                ORDER BY m.name, m.id
                """).stream().filter(row -> matchesTags(row.get("tags"), null, tags))
                .map(row -> merchantView(row, false)).toList();
    }

    public Map<String, Object> foodMerchant(String id) {
        V3Support.uuid(id, "id");
        return publicOne("""
                SELECT m.* FROM merchant m
                WHERE m.id=? AND m.catalog_status='PUBLISHED' AND EXISTS (
                  SELECT 1 FROM food_item f WHERE f.merchant_id=m.id AND f.catalog_status='PUBLISHED')
                """, id, row -> merchantView(row, false));
    }

    public List<Map<String, Object>> foods(String merchantId, String categoryTag, List<String> tags) {
        foodMerchant(merchantId);
        return jdbc.queryForList("""
                SELECT f.*, m.name AS merchant_name, m.version AS merchant_version,
                       m.catalog_status AS merchant_catalog_status
                FROM food_item f JOIN merchant m ON m.id=f.merchant_id
                WHERE f.merchant_id=? AND f.catalog_status='PUBLISHED' AND m.catalog_status='PUBLISHED'
                ORDER BY f.name, f.id
                """, merchantId).stream().filter(row -> matchesTags(row.get("tags"), categoryTag, tags))
                .map(this::foodView).toList();
    }

    public Map<String, Object> food(String id) {
        V3Support.uuid(id, "id");
        return publicOne("""
                SELECT f.*, m.name AS merchant_name, m.version AS merchant_version,
                       m.catalog_status AS merchant_catalog_status
                FROM food_item f JOIN merchant m ON m.id=f.merchant_id
                WHERE f.id=? AND f.catalog_status='PUBLISHED' AND m.catalog_status='PUBLISHED'
                """, id, this::foodView);
    }

    public List<Map<String, Object>> stays(Integer peopleCount, String roomTypeId, List<String> tags) {
        if (peopleCount != null && peopleCount <= 0) {
            V3Support.bad("peopleCount 必须是正整数");
        }
        if (roomTypeId != null) {
            V3Support.uuid(roomTypeId, "roomTypeId");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("""
                SELECT s.*, m.name AS merchant_name, m.catalog_status AS merchant_catalog_status
                FROM stay_property s JOIN merchant m ON m.id=s.merchant_id
                WHERE s.catalog_status='PUBLISHED' AND m.catalog_status='PUBLISHED'
                ORDER BY s.name, s.id
                """)) {
            if (!matchesTags(row.get("tags"), null, tags)) {
                continue;
            }
            List<Map<String, Object>> rooms = roomRows(row.get("id").toString(), true).stream()
                    .filter(room -> roomTypeId == null || roomTypeId.equals(room.get("id")))
                    .filter(room -> peopleCount == null || number(room.get("max_guests")) >= peopleCount)
                    .map(this::roomView).toList();
            if (!rooms.isEmpty()) {
                result.add(stayView(row, rooms));
            }
        }
        return result;
    }

    public Map<String, Object> stay(String id) {
        V3Support.uuid(id, "id");
        Map<String, Object> row = jdbc.queryForList("""
                SELECT s.*, m.name AS merchant_name, m.catalog_status AS merchant_catalog_status
                FROM stay_property s JOIN merchant m ON m.id=s.merchant_id
                WHERE s.id=? AND s.catalog_status='PUBLISHED' AND m.catalog_status='PUBLISHED'
                """, id).stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return stayView(row, roomRows(id, true).stream().map(this::roomView).toList());
    }

    public Map<String, Object> room(String id) {
        V3Support.uuid(id, "id");
        return publicOne("""
                SELECT r.*, s.name AS stay_property_name, s.catalog_status AS stay_catalog_status,
                       m.catalog_status AS merchant_catalog_status
                FROM room_type r JOIN stay_property s ON s.id=r.stay_property_id
                JOIN merchant m ON m.id=s.merchant_id
                WHERE r.id=? AND r.catalog_status='PUBLISHED' AND s.catalog_status='PUBLISHED'
                  AND m.catalog_status='PUBLISHED'
                """, id, this::roomView);
    }

    public Map<String, Object> places(String category, List<String> tags) {
        List<Map<String, Object>> places = jdbc.queryForList("""
                SELECT * FROM place WHERE catalog_status='PUBLISHED' ORDER BY name, id
                """).stream()
                .filter(row -> category == null || category.equals(row.get("category")))
                .filter(row -> matchesTags(row.get("tags"), null, tags))
                .map(this::placeView).toList();
        return V3Support.map("mapMode", "SCHEMATIC", "navigationAvailable", false,
                "scale", "NOT_TO_SCALE", "notice", MAP_NOTICE, "places", places);
    }

    public Map<String, Object> place(String id) {
        V3Support.uuid(id, "id");
        return publicOne("SELECT * FROM place WHERE id=? AND catalog_status='PUBLISHED'", id, this::placeView);
    }

    public List<Map<String, Object>> adminList(String kind) {
        String sql = switch (kind) {
            case "merchants" -> "SELECT * FROM merchant ORDER BY created_at DESC, id DESC";
            case "products" -> "SELECT p.*,m.name merchant_name,m.version merchant_version,m.catalog_status merchant_catalog_status FROM product p JOIN merchant m ON m.id=p.merchant_id ORDER BY p.created_at DESC,p.id DESC";
            case "foods" -> "SELECT f.*,m.name merchant_name,m.version merchant_version,m.catalog_status merchant_catalog_status FROM food_item f JOIN merchant m ON m.id=f.merchant_id ORDER BY f.created_at DESC,f.id DESC";
            case "stays" -> "SELECT s.*,m.name merchant_name,m.catalog_status merchant_catalog_status FROM stay_property s JOIN merchant m ON m.id=s.merchant_id ORDER BY s.created_at DESC,s.id DESC";
            case "room-types" -> "SELECT r.*,s.name stay_property_name,s.catalog_status stay_catalog_status,m.catalog_status merchant_catalog_status FROM room_type r JOIN stay_property s ON s.id=r.stay_property_id JOIN merchant m ON m.id=s.merchant_id ORDER BY r.created_at DESC,r.id DESC";
            case "places" -> "SELECT * FROM place ORDER BY created_at DESC,id DESC";
            default -> throw invalidKind();
        };
        return jdbc.queryForList(sql).stream().map(row -> adminView(kind, row)).toList();
    }

    public Map<String, Object> adminGet(String kind, String id) {
        V3Support.uuid(id, "id");
        String sql = switch (kind) {
            case "merchants" -> "SELECT * FROM merchant WHERE id=?";
            case "products" -> "SELECT p.*,m.name merchant_name,m.version merchant_version,m.catalog_status merchant_catalog_status FROM product p JOIN merchant m ON m.id=p.merchant_id WHERE p.id=?";
            case "foods" -> "SELECT f.*,m.name merchant_name,m.version merchant_version,m.catalog_status merchant_catalog_status FROM food_item f JOIN merchant m ON m.id=f.merchant_id WHERE f.id=?";
            case "stays" -> "SELECT s.*,m.name merchant_name,m.catalog_status merchant_catalog_status FROM stay_property s JOIN merchant m ON m.id=s.merchant_id WHERE s.id=?";
            case "room-types" -> "SELECT r.*,s.name stay_property_name,s.catalog_status stay_catalog_status,m.catalog_status merchant_catalog_status FROM room_type r JOIN stay_property s ON s.id=r.stay_property_id JOIN merchant m ON m.id=s.merchant_id WHERE r.id=?";
            case "places" -> "SELECT * FROM place WHERE id=?";
            default -> throw invalidKind();
        };
        Map<String, Object> row = jdbc.queryForList(sql, id).stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return adminView(kind, row);
    }

    @Transactional
    public Map<String, Object> adminCreate(String kind, Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        switch (kind) {
            case "merchants" -> createMerchant(id, body);
            case "products" -> createProduct(id, body);
            case "foods" -> createFood(id, body);
            case "stays" -> createStay(id, body);
            case "room-types" -> createRoom(id, body);
            case "places" -> createPlace(id, body);
            default -> throw invalidKind();
        }
        return adminGet(kind, id);
    }

    @Transactional
    public Map<String, Object> adminPatch(String kind, String id, Map<String, Object> body) {
        V3Support.uuid(id, "id");
        if (!body.containsKey("expectedVersion")) {
            V3Support.bad("expectedVersion 必填");
        }
        int expectedVersion = V3Support.positiveInt(body, "expectedVersion");
        Set<String> editable = editableFields(kind);
        V3Support.onlyKeys(body, union(editable, Set.of("expectedVersion")).toArray(String[]::new));
        if (body.size() == 1) {
            V3Support.bad("至少需要修改一个业务字段");
        }
        List<String> assignments = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        for (String key : body.keySet()) {
            if ("expectedVersion".equals(key)) {
                continue;
            }
            appendPatch(kind, key, body, assignments, parameters);
        }
        assignments.add("version=version+1");
        parameters.add(id);
        parameters.add(expectedVersion);
        int updated = jdbc.update("UPDATE " + table(kind) + " SET " + String.join(",", assignments)
                + " WHERE id=? AND version=?", parameters.toArray());
        if (updated != 1) {
            ensureExists(kind, id);
            throw versionConflict(expectedVersion, currentVersion(kind, id));
        }
        return adminGet(kind, id);
    }

    @Transactional
    public Map<String, Object> adminStatus(String kind, String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "expectedVersion", "catalogStatus");
        int expectedVersion = V3Support.positiveInt(body, "expectedVersion");
        String status = V3Support.rawRequiredText(body, "catalogStatus");
        if (!Set.of("PUBLISHED", "UNPUBLISHED", "ARCHIVED").contains(status)) {
            V3Support.bad("catalogStatus 无效");
        }
        int updated = jdbc.update("UPDATE " + table(kind)
                        + " SET catalog_status=?,version=version+1 WHERE id=? AND version=?",
                status, V3Support.uuid(id, "id"), expectedVersion);
        if (updated != 1) {
            ensureExists(kind, id);
            throw versionConflict(expectedVersion, currentVersion(kind, id));
        }
        return adminGet(kind, id);
    }

    public List<Map<String, Object>> search(String kind, String keywords, Integer limit, String merchantId) {
        int maximum = limit == null ? 10 : limit;
        if (maximum < 1 || maximum > 10) {
            V3Support.bad("limit 必须在 1 到 10 之间");
        }
        String normalized = keywords == null ? "" : keywords.trim().toLowerCase();
        List<Map<String, Object>> source = switch (kind) {
            case "products" -> products(null, List.of());
            case "foods" -> foods(V3Support.uuid(merchantId, "merchantId"), null, List.of());
            case "stays" -> stays(null, null, List.of());
            case "places" -> ((List<Map<String, Object>>) places(null, List.of()).get("places"));
            default -> throw invalidKind();
        };
        return source.stream().filter(item -> normalized.isEmpty() || V3Support.canonicalJson(item)
                        .toLowerCase().contains(normalized)).limit(maximum).toList();
    }

    private Map<String, Object> productView(Map<String, Object> row) {
        return V3Support.map("id", row.get("id"), "merchantId", row.get("merchant_id"),
                "merchantName", row.get("merchant_name"), "name", row.get("name"),
                "description", row.get("description"), "referencePrice", null,
                "demoPrice", price(row, "ITEM"), "pickupPoint", row.get("pickup_point"),
                "tags", V3Support.tags(row.get("tags")), "imageUrl", row.get("image_url"),
                "orderable", orderable(row, "ITEM"), "demoData", bool(row.get("demo_data")),
                "verificationStatus", row.get("verification_status"), "catalogStatus", row.get("catalog_status"),
                "version", number(row.get("version")));
    }

    private Map<String, Object> foodView(Map<String, Object> row) {
        return V3Support.map("id", row.get("id"), "merchantId", row.get("merchant_id"),
                "merchantName", row.get("merchant_name"), "name", row.get("name"),
                "description", row.get("description"), "itemType", row.get("item_type"),
                "referencePrice", null, "demoPrice", price(row, "PORTION"),
                "visitTimeText", row.get("visit_time_text"), "tags", V3Support.tags(row.get("tags")),
                "imageUrl", row.get("image_url"), "orderable", orderable(row, "PORTION"),
                "demoData", bool(row.get("demo_data")), "verificationStatus", row.get("verification_status"),
                "catalogStatus", row.get("catalog_status"), "version", number(row.get("version")));
    }

    private Map<String, Object> merchantView(Map<String, Object> row, boolean admin) {
        LinkedHashMap<String, Object> view = V3Support.map("id", row.get("id"), "name", row.get("name"),
                "description", row.get("description"));
        if (admin) {
            view.put("contactPhone", row.get("contact_phone"));
        }
        view.putAll(V3Support.map("tags", V3Support.tags(row.get("tags")), "imageUrl", row.get("image_url"),
                "demoData", bool(row.get("demo_data")), "verificationStatus", row.get("verification_status"),
                "catalogStatus", row.get("catalog_status"), "version", number(row.get("version"))));
        return view;
    }

    private Map<String, Object> stayView(Map<String, Object> row, List<Map<String, Object>> rooms) {
        return V3Support.map("id", row.get("id"), "merchantId", row.get("merchant_id"),
                "merchantName", row.get("merchant_name"), "name", row.get("name"),
                "description", row.get("description"), "locationText", row.get("location_text"),
                "tags", V3Support.tags(row.get("tags")), "imageUrl", row.get("image_url"),
                "demoData", bool(row.get("demo_data")), "verificationStatus", row.get("verification_status"),
                "catalogStatus", row.get("catalog_status"), "version", number(row.get("version")),
                "roomTypes", rooms);
    }

    private Map<String, Object> roomView(Map<String, Object> row) {
        return V3Support.map("id", row.get("id"), "stayPropertyId", row.get("stay_property_id"),
                "stayPropertyName", row.get("stay_property_name"), "name", row.get("name"),
                "description", row.get("description"), "maxGuestsPerRoom", number(row.get("max_guests")),
                "referencePrice", null, "demoPrice", price(row, "ROOM_NIGHT"),
                "imageUrl", row.get("image_url"), "orderable", orderable(row, "ROOM_NIGHT"),
                "demoData", bool(row.get("demo_data")), "verificationStatus", row.get("verification_status"),
                "catalogStatus", row.get("catalog_status"), "version", number(row.get("version")));
    }

    private Map<String, Object> placeView(Map<String, Object> row) {
        Object position = row.get("schematic_x") == null || row.get("schematic_y") == null ? null
                : V3Support.map("x", decimal4(row.get("schematic_x")), "y", decimal4(row.get("schematic_y")));
        return V3Support.map("id", row.get("id"), "name", row.get("name"), "category", row.get("category"),
                "description", row.get("description"), "tags", V3Support.tags(row.get("tags")),
                "imageUrl", row.get("image_url"), "schematicPosition", position,
                "demoData", bool(row.get("demo_data")), "verificationStatus", row.get("verification_status"),
                "catalogStatus", row.get("catalog_status"), "version", number(row.get("version")));
    }

    private Map<String, Object> adminView(String kind, Map<String, Object> row) {
        return switch (kind) {
            case "merchants" -> merchantView(row, true);
            case "products" -> productView(row);
            case "foods" -> foodView(row);
            case "stays" -> stayView(row, roomRows(row.get("id").toString(), false).stream()
                    .map(this::roomView).toList());
            case "room-types" -> roomView(row);
            case "places" -> placeView(row);
            default -> throw invalidKind();
        };
    }

    private List<Map<String, Object>> roomRows(String stayId, boolean publicOnly) {
        return jdbc.queryForList("""
                SELECT r.*, s.name AS stay_property_name, s.catalog_status AS stay_catalog_status,
                       m.catalog_status AS merchant_catalog_status
                FROM room_type r JOIN stay_property s ON s.id=r.stay_property_id
                JOIN merchant m ON m.id=s.merchant_id
                WHERE r.stay_property_id=?
                """ + (publicOnly ? " AND r.catalog_status='PUBLISHED' AND s.catalog_status='PUBLISHED' AND m.catalog_status='PUBLISHED'" : "")
                + " ORDER BY r.name,r.id", stayId);
    }

    private Map<String, Object> price(Map<String, Object> row, String unit) {
        if (row.get("price") == null) {
            return null;
        }
        return V3Support.map("amount", V3Support.money(row.get("price")), "currency", "CNY", "unit", unit,
                "simulationNote", row.get("demo_price_note"));
    }

    private boolean orderable(Map<String, Object> row, String unit) {
        return row.get("price") != null && new BigDecimal(row.get("price").toString()).signum() > 0
                && "PUBLISHED".equals(row.get("catalog_status"))
                && (!row.containsKey("merchant_catalog_status") || "PUBLISHED".equals(row.get("merchant_catalog_status")))
                && (!row.containsKey("stay_catalog_status") || "PUBLISHED".equals(row.get("stay_catalog_status")));
    }

    private void createMerchant(String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "name", "description", "contactPhone", "tags", "imageUrl");
        jdbc.update("""
                INSERT INTO merchant(id,name,description,contact_phone,tags,image_url,demo_data,verification_status,catalog_status,version)
                VALUES (?,?,?,?,?,?,true,'UNVERIFIED','UNPUBLISHED',1)
                """, id, V3Support.requiredText(body, "name", 1, 120),
                V3Support.requiredText(body, "description", 1, 500), V3Support.optionalText(body, "contactPhone", 32),
                String.join(",", V3Support.stringList(body, "tags", 20, 40)),
                V3Support.optionalText(body, "imageUrl", 500));
    }

    private void createProduct(String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "merchantId", "name", "description", "pickupPoint", "demoPrice", "tags", "imageUrl");
        String merchantId = V3Support.uuid(body, "merchantId");
        ensureExists("merchants", merchantId);
        DemoPrice price = demoPrice(body, "ITEM");
        jdbc.update("""
                INSERT INTO product(id,merchant_id,name,description,price,demo_price_note,pickup_point,tags,image_url,demo_data,verification_status,catalog_status,version)
                VALUES (?,?,?,?,?,?,?,?,?,true,'UNVERIFIED','UNPUBLISHED',1)
                """, id, merchantId, V3Support.requiredText(body, "name", 1, 120),
                V3Support.requiredText(body, "description", 1, 1000), price.amount(), price.note(),
                V3Support.requiredText(body, "pickupPoint", 1, 160),
                String.join(",", V3Support.stringList(body, "tags", 20, 40)),
                V3Support.optionalText(body, "imageUrl", 500));
    }

    private void createFood(String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "merchantId", "name", "description", "itemType", "demoPrice",
                "visitTimeText", "tags", "imageUrl");
        String merchantId = V3Support.uuid(body, "merchantId");
        ensureExists("merchants", merchantId);
        String itemType = enumValue(body, "itemType", Set.of("DISH", "DRINK", "SET"));
        DemoPrice price = demoPrice(body, "PORTION");
        jdbc.update("""
                INSERT INTO food_item(id,merchant_id,name,description,item_type,price,demo_price_note,visit_time_text,tags,image_url,demo_data,verification_status,catalog_status,version)
                VALUES (?,?,?,?,?,?,?,?,?,?,true,'UNVERIFIED','UNPUBLISHED',1)
                """, id, merchantId, V3Support.requiredText(body, "name", 1, 120),
                V3Support.requiredText(body, "description", 1, 1000), itemType, price.amount(), price.note(),
                V3Support.optionalText(body, "visitTimeText", 160),
                String.join(",", V3Support.stringList(body, "tags", 20, 40)),
                V3Support.optionalText(body, "imageUrl", 500));
    }

    private void createStay(String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "merchantId", "name", "description", "locationText", "tags", "imageUrl");
        String merchantId = V3Support.uuid(body, "merchantId");
        ensureExists("merchants", merchantId);
        jdbc.update("""
                INSERT INTO stay_property(id,merchant_id,name,description,location_text,tags,image_url,demo_data,verification_status,catalog_status,version)
                VALUES (?,?,?,?,?,?,?,true,'UNVERIFIED','UNPUBLISHED',1)
                """, id, merchantId, V3Support.requiredText(body, "name", 1, 120),
                V3Support.requiredText(body, "description", 1, 1000), V3Support.optionalText(body, "locationText", 160),
                String.join(",", V3Support.stringList(body, "tags", 20, 40)),
                V3Support.optionalText(body, "imageUrl", 500));
    }

    private void createRoom(String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "stayPropertyId", "name", "description", "maxGuestsPerRoom", "demoPrice", "imageUrl");
        String stayId = V3Support.uuid(body, "stayPropertyId");
        ensureExists("stays", stayId);
        DemoPrice price = demoPrice(body, "ROOM_NIGHT");
        jdbc.update("""
                INSERT INTO room_type(id,stay_property_id,name,description,max_guests,price,demo_price_note,image_url,demo_data,verification_status,catalog_status,version)
                VALUES (?,?,?,?,?,?,?,?,true,'UNVERIFIED','UNPUBLISHED',1)
                """, id, stayId, V3Support.requiredText(body, "name", 1, 120),
                V3Support.requiredText(body, "description", 1, 1000), V3Support.positiveInt(body, "maxGuestsPerRoom"),
                price.amount(), price.note(), V3Support.optionalText(body, "imageUrl", 500));
    }

    private void createPlace(String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "name", "category", "description", "tags", "imageUrl", "schematicPosition");
        Position position = position(body);
        jdbc.update("""
                INSERT INTO place(id,name,category,description,tags,image_url,schematic_x,schematic_y,demo_data,verification_status,catalog_status,version)
                VALUES (?,?,?,?,?,?,?,?,true,'UNVERIFIED','UNPUBLISHED',1)
                """, id, V3Support.requiredText(body, "name", 1, 120),
                V3Support.requiredText(body, "category", 1, 64), V3Support.requiredText(body, "description", 1, 1000),
                String.join(",", V3Support.stringList(body, "tags", 20, 40)),
                V3Support.optionalText(body, "imageUrl", 500), position.x(), position.y());
    }

    private void appendPatch(String kind, String key, Map<String, Object> body,
                             List<String> assignments, List<Object> parameters) {
        if ("demoPrice".equals(key)) {
            String unit = "products".equals(kind) ? "ITEM" : "foods".equals(kind) ? "PORTION" : "ROOM_NIGHT";
            DemoPrice price = demoPrice(body, unit);
            assignments.add("price=?");
            assignments.add("demo_price_note=?");
            parameters.add(price.amount());
            parameters.add(price.note());
            return;
        }
        if ("schematicPosition".equals(key)) {
            Position position = position(body);
            assignments.add("schematic_x=?");
            assignments.add("schematic_y=?");
            parameters.add(position.x());
            parameters.add(position.y());
            return;
        }
        if ("tags".equals(key)) {
            assignments.add("tags=?");
            parameters.add(String.join(",", V3Support.stringList(body, "tags", 20, 40)));
            return;
        }
        if ("maxGuestsPerRoom".equals(key)) {
            assignments.add("max_guests=?");
            parameters.add(V3Support.positiveInt(body, key));
            return;
        }
        if ("itemType".equals(key)) {
            assignments.add("item_type=?");
            parameters.add(enumValue(body, key, Set.of("DISH", "DRINK", "SET")));
            return;
        }
        Map<String, String> columns = Map.of("name", "name", "description", "description",
                "contactPhone", "contact_phone", "imageUrl", "image_url", "pickupPoint", "pickup_point",
                "visitTimeText", "visit_time_text", "locationText", "location_text", "category", "category");
        String column = columns.get(key);
        if (column == null) {
            V3Support.bad("字段不可编辑");
        }
        assignments.add(column + "=?");
        boolean nullable = Set.of("contactPhone", "imageUrl", "visitTimeText", "locationText").contains(key);
        int max = switch (key) {
            case "name" -> 120;
            case "description" -> 1000;
            case "contactPhone" -> 32;
            case "imageUrl" -> 500;
            case "pickupPoint", "visitTimeText", "locationText" -> 160;
            case "category" -> 64;
            default -> 1000;
        };
        parameters.add(nullable ? V3Support.optionalText(body, key, max)
                : V3Support.requiredText(body, key, 1, max));
    }

    private DemoPrice demoPrice(Map<String, Object> body, String expectedUnit) {
        if (!body.containsKey("demoPrice") || body.get("demoPrice") == null) {
            return new DemoPrice(null, null);
        }
        Map<String, Object> value = V3Support.object(body, "demoPrice");
        V3Support.onlyKeys(value, "amount", "currency", "unit", "simulationNote");
        String amount = V3Support.rawRequiredText(value, "amount");
        if (!amount.matches("^(0|[1-9][0-9]{0,9})\\.[0-9]{2}$")) {
            V3Support.bad("demoPrice.amount 格式无效");
        }
        if (!"CNY".equals(V3Support.rawRequiredText(value, "currency"))
                || !expectedUnit.equals(V3Support.rawRequiredText(value, "unit"))) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "PRICE_UNIT_UNSUPPORTED", "演示价单位不受支持");
        }
        String note = V3Support.requiredText(value, "simulationNote", 1, 200);
        return new DemoPrice(new BigDecimal(amount).setScale(2, RoundingMode.UNNECESSARY), note);
    }

    private Position position(Map<String, Object> body) {
        if (!body.containsKey("schematicPosition") || body.get("schematicPosition") == null) {
            return new Position(null, null);
        }
        Map<String, Object> value = V3Support.object(body, "schematicPosition");
        V3Support.onlyKeys(value, "x", "y");
        return new Position(coordinate(value, "x"), coordinate(value, "y"));
    }

    private static BigDecimal coordinate(Map<String, Object> value, String key) {
        String raw = V3Support.rawRequiredText(value, key);
        if (!raw.matches("^(0\\.[0-9]{4}|1\\.0000)$")) {
            V3Support.bad("示意坐标格式无效");
        }
        return new BigDecimal(raw);
    }

    private static String enumValue(Map<String, Object> body, String key, Set<String> allowed) {
        String value = V3Support.rawRequiredText(body, key);
        if (!allowed.contains(value)) {
            V3Support.bad("字段 " + key + " 枚举值无效");
        }
        return value;
    }

    private Set<String> editableFields(String kind) {
        return switch (kind) {
            case "merchants" -> Set.of("name", "description", "contactPhone", "tags", "imageUrl");
            case "products" -> Set.of("name", "description", "pickupPoint", "demoPrice", "tags", "imageUrl");
            case "foods" -> Set.of("name", "description", "itemType", "demoPrice", "visitTimeText", "tags", "imageUrl");
            case "stays" -> Set.of("name", "description", "locationText", "tags", "imageUrl");
            case "room-types" -> Set.of("name", "description", "maxGuestsPerRoom", "demoPrice", "imageUrl");
            case "places" -> Set.of("name", "category", "description", "tags", "imageUrl", "schematicPosition");
            default -> throw invalidKind();
        };
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return result;
    }

    private String table(String kind) {
        return switch (kind) {
            case "merchants" -> "merchant";
            case "products" -> "product";
            case "foods" -> "food_item";
            case "stays" -> "stay_property";
            case "room-types" -> "room_type";
            case "places" -> "place";
            default -> throw invalidKind();
        };
    }

    private void ensureExists(String kind, String id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table(kind) + " WHERE id=?", Integer.class, id);
        if (count == null || count == 0) {
            V3Support.notFound();
        }
    }

    private int currentVersion(String kind, String id) {
        Integer version = jdbc.queryForObject("SELECT version FROM " + table(kind) + " WHERE id=?", Integer.class, id);
        return version == null ? 0 : version;
    }

    private static ApiRequestException versionConflict(int expected, int current) {
        return new ApiRequestException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "资源版本已变化",
                V3Support.map("kind", "version_conflict", "expectedVersion", expected, "currentVersion", current));
    }

    private static ApiRequestException invalidKind() {
        return new ApiRequestException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "未找到该目录资源");
    }

    private boolean matchesTags(Object csv, String categoryTag, Collection<String> required) {
        Set<String> actual = new LinkedHashSet<>(V3Support.tags(csv));
        return (categoryTag == null || actual.contains(categoryTag))
                && (required == null || actual.containsAll(required));
    }

    private <T> T publicOne(String sql, String id, java.util.function.Function<Map<String, Object>, T> mapper) {
        Map<String, Object> row = jdbc.queryForList(sql, id).stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return mapper.apply(row);
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : ((Number) value).intValue() != 0;
    }

    private static String decimal4(Object value) {
        return new BigDecimal(value.toString()).setScale(4, RoundingMode.UNNECESSARY).toPlainString();
    }

    private record DemoPrice(BigDecimal amount, String note) {
    }

    private record Position(BigDecimal x, BigDecimal y) {
    }
}
