package com.guizhou.wudong.v3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guizhou.wudong.api.ApiRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Service
public class V3PersonalService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final V3CatalogService catalogService;
    private final V3CandidateClient candidateClient;
    private final TransactionTemplate transaction;

    public V3PersonalService(JdbcTemplate jdbc, ObjectMapper objectMapper, V3CatalogService catalogService,
                             V3CandidateClient candidateClient, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.catalogService = catalogService;
        this.candidateClient = candidateClient;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public List<Map<String, Object>> itineraries(String accountId) {
        return jdbc.queryForList("""
                SELECT * FROM saved_itinerary WHERE account_id=? ORDER BY updated_at DESC,id DESC
                """, accountId).stream().map(this::itineraryView).toList();
    }

    public Map<String, Object> itinerary(String accountId, String id) {
        return itineraryView(owned("saved_itinerary", accountId, id));
    }

    public List<Map<String, Object>> drafts(String type, String accountId) {
        return jdbc.queryForList("SELECT * FROM " + draftTable(type)
                + " WHERE account_id=? ORDER BY updated_at DESC,id DESC", accountId).stream()
                .map(row -> draftView(type, row)).toList();
    }

    public Map<String, Object> draft(String type, String accountId, String id) {
        return draftView(type, owned(draftTable(type), accountId, id));
    }

    public SaveResult createItinerary(String accountId, String requestKey, Map<String, Object> body) {
        V3Support.onlyKeys(body, "content");
        Map<String, Object> content = itineraryContent(V3Support.object(body, "content"));
        return save(accountId, requestKey, "CREATE_ITINERARY", "ITINERARY", null,
                V3Support.map("content", content), () -> {
                    String id = UUID.randomUUID().toString();
                    jdbc.update("INSERT INTO saved_itinerary(id,account_id,version,content_json,demo_data) VALUES (?,?,1,?,true)",
                            id, accountId, json(content));
                    return new Applied(id, 1);
                }, id -> itinerary(accountId, id));
    }

    public SaveResult saveItinerary(String accountId, String id, String requestKey, Map<String, Object> body) {
        V3Support.onlyKeys(body, "expectedVersion", "content");
        V3Support.uuid(id, "id");
        int expectedVersion = V3Support.positiveInt(body, "expectedVersion");
        Map<String, Object> content = itineraryContent(V3Support.object(body, "content"));
        return save(accountId, requestKey, "SAVE_ITINERARY", "ITINERARY", id,
                V3Support.map("expectedVersion", expectedVersion, "content", content), () -> {
                    int updated = jdbc.update("""
                            UPDATE saved_itinerary SET content_json=?,version=version+1
                            WHERE id=? AND account_id=? AND version=?
                            """, json(content), id, accountId, expectedVersion);
                    if (updated != 1) {
                        versionFailure("saved_itinerary", accountId, id, expectedVersion);
                    }
                    return new Applied(id, expectedVersion + 1);
                }, value -> itinerary(accountId, value));
    }

    public SaveResult saveDraft(String type, String accountId, String id, String requestKey,
                                Map<String, Object> body) {
        V3Support.onlyKeys(body, "expectedVersion", "content");
        V3Support.uuid(id, "id");
        int expectedVersion = V3Support.positiveInt(body, "expectedVersion");
        Map<String, Object> content = draftContent(type, V3Support.object(body, "content"), false);
        String operation = "FOOD".equals(type) ? "SAVE_FOOD_DRAFT" : "SAVE_STAY_DRAFT";
        String resource = type + "_DRAFT";
        return save(accountId, requestKey, operation, resource, id,
                V3Support.map("expectedVersion", expectedVersion, "content", content), () -> {
                    Map<String, Object> current = owned(draftTable(type), accountId, id);
                    if (!"DRAFT".equals(current.get("state"))) {
                        throw alreadySubmitted(type, current);
                    }
                    int updated = jdbc.update("UPDATE " + draftTable(type)
                                    + " SET content_json=?,version=version+1 WHERE id=? AND account_id=? AND version=? AND state='DRAFT'",
                            json(content), id, accountId, expectedVersion);
                    if (updated != 1) {
                        versionFailure(draftTable(type), accountId, id, expectedVersion);
                    }
                    return new Applied(id, expectedVersion + 1);
                }, value -> draft(type, accountId, value));
    }

    public SaveResult adopt(String type, boolean create, String accountId, String id, String requestKey,
                            Map<String, Object> body, String userProof, String anonymousProof) {
        if (create) {
            V3Support.onlyKeys(body, "candidateRef");
        } else {
            V3Support.onlyKeys(body, "candidateRef", "expectedVersion");
            V3Support.uuid(id, "id");
        }
        Map<String, Object> candidateRef = V3Support.object(body, "candidateRef");
        V3CandidateClient.validateCandidateRef(candidateRef);
        int expectedVersion = create ? 0 : V3Support.positiveInt(body, "expectedVersion");
        String resourceType = type;
        String draftType = "FOOD_DRAFT".equals(resourceType) ? "FOOD"
                : "STAY_DRAFT".equals(resourceType) ? "STAY" : null;
        String action = create ? "CREATE" : "UPDATE";
        Map<String, Object> expectedBase = create ? null : V3Support.map("resourceType", resourceType,
                "resourceId", id, "resourceVersion", expectedVersion);
        String operation = "ADOPT_" + resourceType + "_" + action;
        Map<String, Object> normalizedRequest = create
                ? V3Support.map("candidateRef", candidateRef)
                : V3Support.map("candidateRef", candidateRef, "expectedVersion", expectedVersion);
        Map<String, Object> existing = reserve(accountId, requestKey, operation, resourceType, id, normalizedRequest);
        if (existing != null && "SUCCEEDED".equals(existing.get("state"))) {
            return replay(existing, accountId, resourceType);
        }
        Map<String, Object> resolved = candidateClient.resolve(candidateRef, resourceType, action, expectedBase,
                userProof, anonymousProof);
        @SuppressWarnings("unchecked") Map<String, Object> payload = (Map<String, Object>) resolved.get("payload");
        Map<String, Object> content = switch (resourceType) {
            case "ITINERARY" -> itineraryContent(payload);
            case "FOOD_DRAFT" -> draftContent("FOOD", payload, true);
            case "STAY_DRAFT" -> draftContent("STAY", payload, true);
            default -> throw new IllegalArgumentException("候选类型无效");
        };
        String sourceThread = candidateRef.get("threadId").toString();
        Applied applied = transaction.execute(status -> {
            lockReceipt(accountId, operation, requestKey);
            if (create) {
                String newId = UUID.randomUUID().toString();
                if ("ITINERARY".equals(resourceType)) {
                    jdbc.update("""
                            INSERT INTO saved_itinerary(id,account_id,version,content_json,demo_data,source_thread_id)
                            VALUES (?,?,1,?,true,?)
                            """, newId, accountId, json(content), sourceThread);
                } else {
                    Map<String, Object> complete = withPrivateDraftFields(content, null);
                    jdbc.update("INSERT INTO " + draftTable(draftType)
                                    + "(id,account_id,version,state,content_json,source_thread_id,demo_data) VALUES (?,?,1,'DRAFT',?,?,true)",
                            newId, accountId, json(complete), sourceThread);
                }
                finishReceipt(accountId, operation, requestKey, resourceType, newId, 1);
                return new Applied(newId, 1);
            }
            Map<String, Object> current = owned(resourceTable(resourceType), accountId, id);
            if (resourceType.endsWith("_DRAFT") && !"DRAFT".equals(current.get("state"))) {
                throw alreadySubmitted(draftType, current);
            }
            Map<String, Object> stored = content;
            if (resourceType.endsWith("_DRAFT")) {
                stored = withPrivateDraftFields(content, readJson(current.get("content_json")));
            }
            int updated = jdbc.update("UPDATE " + resourceTable(resourceType)
                            + " SET content_json=?,source_thread_id=?,version=version+1"
                            + " WHERE id=? AND account_id=? AND version=?"
                            + (resourceType.endsWith("_DRAFT") ? " AND state='DRAFT'" : ""),
                    json(stored), sourceThread, id, accountId, expectedVersion);
            if (updated != 1) {
                versionFailure(resourceTable(resourceType), accountId, id, expectedVersion);
            }
            finishReceipt(accountId, operation, requestKey, resourceType, id, expectedVersion + 1);
            return new Applied(id, expectedVersion + 1);
        });
        if (applied == null) {
            throw unavailable();
        }
        Map<String, Object> receipt = receipt(accountId, operation, requestKey);
        return new SaveResult(writeReceipt(receipt, loadResource(resourceType, accountId, applied.id()), false), false);
    }

    public Map<String, Object> internalProjection(String resourceType, String accountId, String id) {
        if ("ITINERARY".equals(resourceType)) {
            return itinerary(accountId, id);
        }
        String type = "FOOD_DRAFT".equals(resourceType) ? "FOOD" : "STAY";
        Map<String, Object> row = owned(draftTable(type), accountId, id);
        Map<String, Object> content = readJson(row.get("content_json"));
        LinkedHashMap<String, Object> projection = V3Support.map("id", row.get("id"), "draftType", type,
                "version", row.get("version"), "state", row.get("state"));
        for (String key : planningKeys(type)) {
            projection.put(key, content.get(key));
        }
        projection.put("demoData", bool(row.get("demo_data")));
        return projection;
    }

    private SaveResult save(String accountId, String requestKey, String operationType, String resourceType,
                            String targetId, Map<String, Object> request, WriteAction action,
                            Function<String, Map<String, Object>> loader) {
        Map<String, Object> existing = reserve(accountId, requestKey, operationType, resourceType, targetId, request);
        if (existing != null && "SUCCEEDED".equals(existing.get("state"))) {
            return new SaveResult(writeReceipt(existing,
                    loader.apply(existing.get("result_resource_id").toString()), true), true);
        }
        Applied applied = transaction.execute(status -> {
            Map<String, Object> locked = lockReceipt(accountId, operationType, requestKey);
            if ("SUCCEEDED".equals(locked.get("state"))) {
                return new Applied(locked.get("result_resource_id").toString(),
                        ((Number) locked.get("committed_version")).intValue());
            }
            Applied value = action.run();
            finishReceipt(accountId, operationType, requestKey, resourceType, value.id(), value.version());
            return value;
        });
        if (applied == null) {
            throw unavailable();
        }
        Map<String, Object> receipt = receipt(accountId, operationType, requestKey);
        return new SaveResult(writeReceipt(receipt, loader.apply(applied.id()), false), false);
    }

    private Map<String, Object> reserve(String accountId, String requestKey, String operationType,
                                        String resourceType, String targetId, Map<String, Object> request) {
        V3Support.uuid(requestKey, "Idempotency-Key");
        String digest = V3Support.sha256(V3Support.map("contractVersion", V3Support.CONTRACT,
                "accountId", accountId, "operationType", operationType, "requestKey", requestKey,
                "target", V3Support.map("resourceType", resourceType, "resourceId", targetId), "request", request));
        return transaction.execute(status -> {
            Map<String, Object> current = jdbc.queryForList("""
                    SELECT * FROM operation_receipt WHERE account_id=? AND operation_type=? AND request_key=? FOR UPDATE
                    """, accountId, operationType, requestKey).stream().findFirst().orElse(null);
            if (current != null) {
                if (!digest.equals(current.get("request_digest"))) {
                    throw new ApiRequestException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                            "请求键已绑定其他请求内容");
                }
                if ("NOT_APPLIED".equals(current.get("state"))) {
                    throw new ApiRequestException(HttpStatus.CONFLICT, "OPERATION_NOT_APPLIED",
                            "该操作已可靠终结且未应用");
                }
                return current;
            }
            jdbc.update("""
                    INSERT INTO operation_receipt(
                      account_id,operation_type,request_key,request_digest,requested_resource_type,
                      requested_resource_id,state)
                    VALUES (?,?,?,?,?,?,'RESERVED')
                    """, accountId, operationType, requestKey, digest, resourceType, targetId);
            return null;
        });
    }

    private Map<String, Object> lockReceipt(String accountId, String operationType, String requestKey) {
        return jdbc.queryForMap("""
                SELECT * FROM operation_receipt WHERE account_id=? AND operation_type=? AND request_key=? FOR UPDATE
                """, accountId, operationType, requestKey);
    }

    private void finishReceipt(String accountId, String operationType, String requestKey, String resourceType,
                               String resourceId, int version) {
        int updated = jdbc.update("""
                UPDATE operation_receipt SET state='SUCCEEDED',result_resource_type=?,result_resource_id=?,
                  committed_version=?,committed_at=CURRENT_TIMESTAMP,terminal_reason=NULL,terminal_at=NULL
                WHERE account_id=? AND operation_type=? AND request_key=? AND state='RESERVED'
                """, resourceType, resourceId, version, accountId, operationType, requestKey);
        if (updated != 1) {
            throw unavailable();
        }
    }

    private SaveResult replay(Map<String, Object> receipt, String accountId, String resourceType) {
        return new SaveResult(writeReceipt(receipt,
                loadResource(resourceType, accountId, receipt.get("result_resource_id").toString()), true), true);
    }

    private Map<String, Object> loadResource(String resourceType, String accountId, String id) {
        return switch (resourceType) {
            case "ITINERARY" -> itinerary(accountId, id);
            case "FOOD_DRAFT" -> draft("FOOD", accountId, id);
            case "STAY_DRAFT" -> draft("STAY", accountId, id);
            default -> throw new IllegalArgumentException("资源类型无效");
        };
    }

    private Map<String, Object> receipt(String accountId, String operationType, String requestKey) {
        return jdbc.queryForMap("""
                SELECT * FROM operation_receipt WHERE account_id=? AND operation_type=? AND request_key=?
                """, accountId, operationType, requestKey);
    }

    private Map<String, Object> writeReceipt(Map<String, Object> receipt, Map<String, Object> resource,
                                             boolean replayed) {
        return V3Support.map("requestKey", receipt.get("request_key"), "operationType", receipt.get("operation_type"),
                "resourceType", receipt.get("result_resource_type"), "resourceId", receipt.get("result_resource_id"),
                "committedVersion", receipt.get("committed_version"), "committedAt", V3Support.utc(receipt.get("committed_at")),
                "replayed", replayed, "resource", resource, "submittedDraft", null);
    }

    private Map<String, Object> itineraryContent(Map<String, Object> content) {
        V3Support.onlyKeys(content, "title", "travelDate", "peopleCount", "days");
        requirePresent(content, "title", "travelDate", "peopleCount", "days");
        LinkedHashMap<String, Object> result = V3Support.map("title",
                V3Support.requiredText(content, "title", 1, 80), "travelDate", nullableDate(content, "travelDate"),
                "peopleCount", nullablePositive(content, "peopleCount"));
        List<Map<String, Object>> days = V3Support.objectList(content, "days", 1, 30);
        List<Map<String, Object>> normalizedDays = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            Map<String, Object> day = days.get(dayIndex);
            V3Support.onlyKeys(day, "day", "theme", "stops");
            requirePresent(day, "day", "theme", "stops");
            if (V3Support.positiveInt(day, "day") != dayIndex + 1) {
                V3Support.bad("days.day 必须与数组顺序连续一致");
            }
            List<Map<String, Object>> stops = V3Support.objectList(day, "stops", 0, 30);
            List<Map<String, Object>> normalizedStops = new ArrayList<>();
            for (int stopIndex = 0; stopIndex < stops.size(); stopIndex++) {
                Map<String, Object> stop = stops.get(stopIndex);
                V3Support.onlyKeys(stop, "sequence", "targetType", "targetId", "title", "note");
                requirePresent(stop, "sequence", "targetType", "targetId", "title", "note");
                if (V3Support.positiveInt(stop, "sequence") != stopIndex + 1) {
                    V3Support.bad("stops.sequence 必须与数组顺序连续一致");
                }
                String targetType = nullableEnum(stop, "targetType",
                        Set.of("PLACE", "PRODUCT", "FOOD", "STAY", "ROUTE_GUIDE"));
                String targetId = nullableUuid(stop, "targetId");
                if ((targetType == null) != (targetId == null)) {
                    V3Support.bad("targetType 与 targetId 必须同时为空或同时出现");
                }
                if (targetType != null) {
                    validatePublicTarget(targetType, targetId);
                }
                normalizedStops.add(V3Support.map("sequence", stopIndex + 1, "targetType", targetType,
                        "targetId", targetId, "title", V3Support.requiredText(stop, "title", 1, 120),
                        "note", nullableTextStrict(stop, "note", 500)));
            }
            normalizedDays.add(V3Support.map("day", dayIndex + 1,
                    "theme", nullableTextStrict(day, "theme", 80), "stops", normalizedStops));
        }
        result.put("days", normalizedDays);
        return result;
    }

    private Map<String, Object> draftContent(String type, Map<String, Object> content, boolean planningOnly) {
        Set<String> planning = planningKeys(type);
        Set<String> all = new HashSet<>(planning);
        if (!planningOnly) {
            all.addAll(Set.of("contactName", "contactPhone", "note"));
        }
        V3Support.onlyKeys(content, all.toArray(String[]::new));
        requirePresent(content, all.toArray(String[]::new));
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if ("FOOD".equals(type)) {
            String merchantId = V3Support.uuid(content, "merchantId");
            catalogService.foodMerchant(merchantId);
            List<Map<String, Object>> items = V3Support.objectList(content, "items", 1, 50);
            Set<String> ids = new HashSet<>();
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Map<String, Object> item : items) {
                V3Support.onlyKeys(item, "foodItemId", "quantity");
                String foodId = V3Support.uuid(item, "foodItemId");
                if (!ids.add(foodId)) {
                    throw new ApiRequestException(HttpStatus.BAD_REQUEST, "DUPLICATE_FOOD_ITEM", "餐食项不能重复");
                }
                Map<String, Object> food = catalogService.food(foodId);
                if (!merchantId.equals(food.get("merchantId"))) {
                    throw new ApiRequestException(HttpStatus.CONFLICT, "FOOD_MERCHANT_MISMATCH", "餐食必须来自同一家店铺");
                }
                normalized.add(V3Support.map("foodItemId", foodId,
                        "quantity", V3Support.positiveInt(item, "quantity")));
            }
            result.put("merchantId", merchantId);
            result.put("items", normalized);
            result.put("visitAt", nullableDateTime(content, "visitAt"));
            result.put("peopleCount", nullablePositive(content, "peopleCount"));
        } else {
            String roomId = V3Support.uuid(content, "roomTypeId");
            Map<String, Object> room = catalogService.room(roomId);
            String checkIn = nullableDate(content, "checkInDate");
            String checkOut = nullableDate(content, "checkOutDate");
            Integer roomCount = nullablePositive(content, "roomCount");
            Integer peopleCount = nullablePositive(content, "peopleCount");
            if (checkIn != null && checkOut != null && !LocalDate.parse(checkOut).isAfter(LocalDate.parse(checkIn))) {
                V3Support.bad("离店日期必须晚于入住日期");
            }
            if (roomCount != null && peopleCount != null
                    && peopleCount > Math.multiplyExact(((Number) room.get("maxGuestsPerRoom")).intValue(), roomCount)) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "CAPACITY_EXCEEDED", "人数超过演示房型容量");
            }
            result.put("roomTypeId", roomId);
            result.put("checkInDate", checkIn);
            result.put("checkOutDate", checkOut);
            result.put("roomCount", roomCount);
            result.put("peopleCount", peopleCount);
        }
        if (!planningOnly) {
            result.put("contactName", nullableTextStrict(content, "contactName", 80));
            result.put("contactPhone", nullablePhone(content, "contactPhone"));
            result.put("note", nullableTextStrict(content, "note", 500));
        }
        return result;
    }

    private Map<String, Object> withPrivateDraftFields(Map<String, Object> planning, Map<String, Object> current) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(planning);
        result.put("contactName", current == null ? null : current.get("contactName"));
        result.put("contactPhone", current == null ? null : current.get("contactPhone"));
        result.put("note", current == null ? null : current.get("note"));
        return result;
    }

    private void validatePublicTarget(String type, String id) {
        switch (type) {
            case "PLACE" -> catalogService.place(id);
            case "PRODUCT" -> catalogService.product(id);
            case "FOOD" -> catalogService.food(id);
            case "STAY" -> catalogService.room(id);
            case "ROUTE_GUIDE" -> {
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM community_post WHERE id=? AND published=true AND post_type='ROUTE_GUIDE'",
                        Integer.class, id);
                if (count == null || count == 0) {
                    V3Support.notFound();
                }
            }
            default -> V3Support.bad("targetType 无效");
        }
    }

    private Map<String, Object> itineraryView(Map<String, Object> row) {
        return V3Support.map("id", row.get("id"), "version", row.get("version"),
                "content", readJson(row.get("content_json")), "demoData", bool(row.get("demo_data")),
                "createdAt", V3Support.utc(row.get("created_at")), "updatedAt", V3Support.utc(row.get("updated_at")));
    }

    private Map<String, Object> draftView(String type, Map<String, Object> row) {
        Map<String, Object> content = readJson(row.get("content_json"));
        if ("SUBMITTED".equals(row.get("state"))) {
            content = new LinkedHashMap<>(content);
            content.remove("contactName");
            content.remove("contactPhone");
        }
        Object linked = row.get("linked_order_id") == null ? null : V3Support.map("orderType", type,
                "orderId", row.get("linked_order_id"));
        return V3Support.map("id", row.get("id"), "draftType", type, "version", row.get("version"),
                "state", row.get("state"), "content", content, "linkedOrder", linked,
                "demoData", bool(row.get("demo_data")), "createdAt", V3Support.utc(row.get("created_at")),
                "updatedAt", V3Support.utc(row.get("updated_at")));
    }

    private Map<String, Object> owned(String table, String accountId, String id) {
        V3Support.uuid(id, "id");
        Map<String, Object> row = jdbc.queryForList("SELECT * FROM " + table + " WHERE id=? AND account_id=?",
                id, accountId).stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return row;
    }

    private void versionFailure(String table, String accountId, String id, int expected) {
        Map<String, Object> current = owned(table, accountId, id);
        if (table.endsWith("_draft") && "SUBMITTED".equals(current.get("state"))) {
            throw alreadySubmitted(table.startsWith("food") ? "FOOD" : "STAY", current);
        }
        throw new ApiRequestException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "资源版本已变化",
                V3Support.map("kind", "version_conflict", "expectedVersion", expected,
                        "currentVersion", ((Number) current.get("version")).intValue()));
    }

    private static ApiRequestException alreadySubmitted(String type, Map<String, Object> row) {
        return new ApiRequestException(HttpStatus.CONFLICT, "DRAFT_ALREADY_SUBMITTED", "草稿已经提交",
                V3Support.map("kind", "draft_already_submitted", "linkedOrder",
                        row.get("linked_order_id") == null ? null : V3Support.map("orderType", type,
                                "orderId", row.get("linked_order_id"))));
    }

    private Map<String, Object> readJson(Object raw) {
        try {
            return objectMapper.readValue(raw.toString(), new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("持久 JSON 无法读取", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("业务内容无法序列化", exception);
        }
    }

    private static Set<String> planningKeys(String type) {
        return "FOOD".equals(type)
                ? Set.of("merchantId", "items", "visitAt", "peopleCount")
                : Set.of("roomTypeId", "checkInDate", "checkOutDate", "roomCount", "peopleCount");
    }

    private static String draftTable(String type) {
        return "FOOD".equals(type) ? "food_draft" : "STAY".equals(type) ? "stay_draft" : invalidType();
    }

    private static String resourceTable(String resourceType) {
        return switch (resourceType) {
            case "ITINERARY" -> "saved_itinerary";
            case "FOOD_DRAFT" -> "food_draft";
            case "STAY_DRAFT" -> "stay_draft";
            default -> throw new IllegalArgumentException("资源类型无效");
        };
    }

    private static String invalidType() {
        throw new IllegalArgumentException("草稿类型无效");
    }

    private static void requirePresent(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            if (!body.containsKey(key)) {
                V3Support.bad("字段 " + key + " 必须出现");
            }
        }
    }

    private static String nullableTextStrict(Map<String, Object> body, String key, int max) {
        if (body.get(key) == null) {
            return null;
        }
        if (!(body.get(key) instanceof String value) || value.isEmpty()) {
            V3Support.bad("字段 " + key + " 必须是非空字符串或 null");
        }
        String normalized = ((String) body.get(key)).trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > max) {
            V3Support.bad("字段 " + key + " 长度不符合要求");
        }
        return normalized;
    }

    private static String nullablePhone(Map<String, Object> body, String key) {
        if (body.get(key) == null) {
            return null;
        }
        Map<String, Object> wrapper = V3Support.map("contactPhone", body.get(key));
        return V3Support.phone(wrapper);
    }

    private static Integer nullablePositive(Map<String, Object> body, String key) {
        if (body.get(key) == null) {
            return null;
        }
        return V3Support.positiveInt(body, key);
    }

    private static String nullableDate(Map<String, Object> body, String key) {
        if (body.get(key) == null) {
            return null;
        }
        return V3Support.date(body, key).toString();
    }

    private static String nullableDateTime(Map<String, Object> body, String key) {
        if (body.get(key) == null) {
            return null;
        }
        return V3Support.dateTime(body, key).toString();
    }

    private static String nullableUuid(Map<String, Object> body, String key) {
        if (body.get(key) == null) {
            return null;
        }
        return V3Support.optionalUuid(body, key);
    }

    private static String nullableEnum(Map<String, Object> body, String key, Set<String> allowed) {
        if (body.get(key) == null) {
            return null;
        }
        String value = V3Support.rawRequiredText(body, key);
        if (!allowed.contains(value)) {
            V3Support.bad("字段 " + key + " 枚举值无效");
        }
        return value;
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : ((Number) value).intValue() != 0;
    }

    private static ApiRequestException unavailable() {
        return new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_RESULT_UNAVAILABLE", "写入结果暂时无法确认");
    }

    public record SaveResult(Map<String, Object> receipt, boolean replayed) {
    }

    private record Applied(String id, int version) {
    }

    @FunctionalInterface
    private interface WriteAction {
        Applied run();
    }
}
