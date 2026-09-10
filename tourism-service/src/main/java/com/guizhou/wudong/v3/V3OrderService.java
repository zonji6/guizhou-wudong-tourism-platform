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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class V3OrderService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;

    public V3OrderService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                          PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> quoteProduct(Map<String, Object> body) {
        V3Support.onlyKeys(body, "productId", "quantity", "pickupPoint");
        return productQuote(body, false).view();
    }

    public Map<String, Object> quoteFood(Map<String, Object> body) {
        V3Support.onlyKeys(body, "merchantId", "items", "visitAt", "peopleCount");
        return foodQuote(body, false).view();
    }

    public Map<String, Object> quoteStay(Map<String, Object> body) {
        V3Support.onlyKeys(body, "roomTypeId", "checkInDate", "checkOutDate", "roomCount", "peopleCount");
        return stayQuote(body, false).view();
    }

    public CreateResult createProduct(String accountId, String requestKey, Map<String, Object> body) {
        V3Support.onlyKeys(body, "productId", "quantity", "pickupPoint", "contactName", "contactPhone",
                "note", "sourceThreadId", "expectedQuoteFingerprint");
        Map<String, Object> selection = V3Support.map("productId", V3Support.uuid(body, "productId"),
                "quantity", V3Support.positiveInt(body, "quantity"),
                "pickupPoint", V3Support.requiredText(body, "pickupPoint", 1, 160));
        Contact contact = contact(body);
        String expectedFingerprint = V3Support.digest(body, "expectedQuoteFingerprint");
        String sourceThreadId = V3Support.optionalUuid(body, "sourceThreadId");
        return create(accountId, requestKey, "CREATE_PRODUCT_ORDER", "PRODUCT_ORDER", body,
                () -> productQuote(selection, true), quote -> {
                    String id = UUID.randomUUID().toString();
                    PriceLine line = quote.lines().getFirst();
                    jdbc.update("""
                            INSERT INTO product_order(
                              id,visitor_id,account_id,product_id,product_name_snapshot,catalog_version_at_order,
                              quantity,pickup_point,contact_name,contact_phone,note,unit,unit_amount,total_amount,
                              currency,status,source_thread_id,source_draft_id,demo_data)
                            VALUES (?,NULL,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING_PICKUP',?,NULL,true)
                            """, id, accountId, line.resourceId(), line.resourceName(), line.catalogVersion(),
                            line.quantity(), selection.get("pickupPoint"), contact.name(), contact.phone(), contact.note(),
                            line.unit(), line.unitAmount(), quote.totalAmount(), "CNY", sourceThreadId);
                    return id;
                }, expectedFingerprint);
    }

    public CreateResult createFood(String accountId, String requestKey, Map<String, Object> body) {
        V3Support.onlyKeys(body, "merchantId", "items", "visitAt", "peopleCount", "contactName",
                "contactPhone", "note", "sourceThreadId", "expectedQuoteFingerprint");
        Map<String, Object> selection = foodSelection(body);
        Contact contact = contact(body);
        String expectedFingerprint = V3Support.digest(body, "expectedQuoteFingerprint");
        String sourceThreadId = V3Support.optionalUuid(body, "sourceThreadId");
        return create(accountId, requestKey, "CREATE_FOOD_ORDER", "FOOD_ORDER", body,
                () -> foodQuote(selection, true), quote -> {
                    String id = UUID.randomUUID().toString();
                    jdbc.update("""
                            INSERT INTO food_order(
                              id,visitor_id,account_id,merchant_id,merchant_name_snapshot,food_item_id,visit_at,
                              people_count,total_amount,currency,contact_name,contact_phone,note,status,
                              source_thread_id,source_draft_id,demo_data)
                            VALUES (?,NULL,?,?,?,NULL,?,?,?,?,?,?,?,'PENDING_VISIT',?,NULL,true)
                            """, id, accountId, selection.get("merchantId"), quote.parentName(),
                            selection.get("visitAt"), selection.get("peopleCount"), quote.totalAmount(), "CNY",
                            contact.name(), contact.phone(), contact.note(), sourceThreadId);
                    for (PriceLine line : quote.lines()) {
                        jdbc.update("""
                                INSERT INTO food_order_item(
                                  id,food_order_id,sequence_no,food_item_id,food_item_name_snapshot,item_type_snapshot,
                                  catalog_version_at_order,unit,unit_amount,quantity,line_amount,demo_data)
                                VALUES (?,?,?,?,?,?,?,?,?,?,?,true)
                                """, UUID.randomUUID().toString(), id, line.sequence(), line.resourceId(),
                                line.resourceName(), line.itemType(), line.catalogVersion(), line.unit(),
                                line.unitAmount(), line.quantity(), line.lineAmount());
                    }
                    return id;
                }, expectedFingerprint);
    }

    public CreateResult createStay(String accountId, String requestKey, Map<String, Object> body) {
        V3Support.onlyKeys(body, "roomTypeId", "checkInDate", "checkOutDate", "roomCount", "peopleCount",
                "contactName", "contactPhone", "note", "sourceThreadId", "expectedQuoteFingerprint");
        Map<String, Object> selection = staySelection(body);
        Contact contact = contact(body);
        String expectedFingerprint = V3Support.digest(body, "expectedQuoteFingerprint");
        String sourceThreadId = V3Support.optionalUuid(body, "sourceThreadId");
        return create(accountId, requestKey, "CREATE_STAY_BOOKING", "STAY_BOOKING", body,
                () -> stayQuote(selection, true), quote -> {
                    String id = UUID.randomUUID().toString();
                    PriceLine line = quote.lines().getFirst();
                    Map<String, Object> details = quote.calculation();
                    jdbc.update("""
                            INSERT INTO stay_booking(
                              id,visitor_id,account_id,stay_property_id,stay_property_name_snapshot,room_type_id,
                              room_type_name_snapshot,catalog_version_at_order,check_in_date,check_out_date,nights,
                              room_count,people_count,max_guests_per_room_at_order,unit,unit_amount,total_amount,currency,
                              contact_name,contact_phone,note,status,source_thread_id,source_draft_id,demo_data)
                            VALUES (?,NULL,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING_CONFIRMATION',?,NULL,true)
                            """, id, accountId, quote.parentId(), quote.parentName(), line.resourceId(), line.resourceName(),
                            line.catalogVersion(), selection.get("checkInDate"), selection.get("checkOutDate"),
                            details.get("nights"), details.get("roomCount"), details.get("peopleCount"),
                            details.get("maxGuestsPerRoom"), line.unit(), line.unitAmount(), quote.totalAmount(), "CNY",
                            contact.name(), contact.phone(), contact.note(), sourceThreadId);
                    return id;
                }, expectedFingerprint);
    }

    public CreateResult submitDraft(String type, String accountId, String draftId, String requestKey,
                                    Map<String, Object> body) {
        V3Support.onlyKeys(body, "expectedVersion", "expectedQuoteFingerprint");
        V3Support.uuid(draftId, "id");
        int expectedVersion = V3Support.positiveInt(body, "expectedVersion");
        String expectedFingerprint = V3Support.digest(body, "expectedQuoteFingerprint");
        String operationType = "FOOD".equals(type) ? "SUBMIT_FOOD_DRAFT" : "SUBMIT_STAY_DRAFT";
        String resourceType = "FOOD".equals(type) ? "FOOD_ORDER" : "STAY_BOOKING";
        String draftTable = "FOOD".equals(type) ? "food_draft" : "stay_draft";
        String digest = V3Support.sha256(V3Support.map("contractVersion", V3Support.CONTRACT,
                "accountId", accountId, "operationType", operationType, "requestKey", requestKey,
                "target", V3Support.map("resourceType", type + "_DRAFT", "resourceId", draftId),
                "request", V3Support.map("expectedVersion", expectedVersion,
                        "expectedQuoteFingerprint", expectedFingerprint)));
        V3Support.uuid(requestKey, "Idempotency-Key");
        Map<String, Object> existing = transaction.execute(status -> reserve(accountId, requestKey, operationType,
                type + "_DRAFT", draftId, digest));
        if (existing != null && "SUCCEEDED".equals(existing.get("state"))) {
            Map<String, Object> resource = myOrder("FOOD".equals(type) ? "foods" : "stays", accountId,
                    existing.get("result_resource_id").toString());
            return new CreateResult(writeReceipt(existing, resource, true), true);
        }
        SubmissionStage stage = transaction.execute(status -> {
            Map<String, Object> receipt = jdbc.queryForMap("""
                    SELECT * FROM operation_receipt
                    WHERE account_id=? AND operation_type=? AND request_key=? FOR UPDATE
                    """, accountId, operationType, requestKey);
            if (!digest.equals(receipt.get("request_digest"))) {
                throw idempotencyConflict();
            }
            if ("SUCCEEDED".equals(receipt.get("state"))) {
                return new SubmissionStage(receipt.get("result_resource_id").toString(), true, null);
            }
            Map<String, Object> draft = jdbc.queryForList("SELECT * FROM " + draftTable
                            + " WHERE id=? AND account_id=? FOR UPDATE", draftId, accountId)
                    .stream().findFirst().orElse(null);
            if (draft == null) {
                V3Support.notFound();
            }
            if (!"DRAFT".equals(draft.get("state"))) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "DRAFT_ALREADY_SUBMITTED", "草稿已经提交",
                        V3Support.map("kind", "draft_already_submitted", "linkedOrder",
                                V3Support.map("orderType", type, "orderId", draft.get("linked_order_id"))));
            }
            int currentVersion = number(draft.get("version"));
            if (currentVersion != expectedVersion) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "草稿版本已变化",
                        V3Support.map("kind", "version_conflict", "expectedVersion", expectedVersion,
                                "currentVersion", currentVersion));
            }
            Map<String, Object> content = readJson(draft.get("content_json"));
            List<String> missing = draftMissing(type, content);
            if (!missing.isEmpty()) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "DRAFT_INCOMPLETE", "草稿信息不完整",
                        V3Support.map("kind", "draft_incomplete", "missingFields", missing));
            }
            Quote quote;
            String orderId = UUID.randomUUID().toString();
            if ("FOOD".equals(type)) {
                Map<String, Object> selection = V3Support.map("merchantId", content.get("merchantId"),
                        "items", content.get("items"), "visitAt", content.get("visitAt"),
                        "peopleCount", content.get("peopleCount"));
                quote = foodQuote(selection, true);
                if (!expectedFingerprint.equals(quote.fingerprint())) {
                    markQuoteChanged(accountId, operationType, requestKey, digest);
                    return new SubmissionStage(null, false, quote.view());
                }
                jdbc.update("""
                        INSERT INTO food_order(
                          id,visitor_id,account_id,merchant_id,merchant_name_snapshot,food_item_id,visit_at,
                          people_count,total_amount,currency,contact_name,contact_phone,note,status,
                          source_thread_id,source_draft_id,demo_data)
                        VALUES (?,NULL,?,?,?,NULL,?,?,?,?,?,?,?,'PENDING_VISIT',?,?,true)
                        """, orderId, accountId, content.get("merchantId"), quote.parentName(), content.get("visitAt"),
                        content.get("peopleCount"), quote.totalAmount(), "CNY", content.get("contactName"),
                        content.get("contactPhone"), content.get("note"), draft.get("source_thread_id"), draftId);
                for (PriceLine line : quote.lines()) {
                    jdbc.update("""
                            INSERT INTO food_order_item(
                              id,food_order_id,sequence_no,food_item_id,food_item_name_snapshot,item_type_snapshot,
                              catalog_version_at_order,unit,unit_amount,quantity,line_amount,demo_data)
                            VALUES (?,?,?,?,?,?,?,?,?,?,?,true)
                            """, UUID.randomUUID().toString(), orderId, line.sequence(), line.resourceId(),
                            line.resourceName(), line.itemType(), line.catalogVersion(), line.unit(), line.unitAmount(),
                            line.quantity(), line.lineAmount());
                }
            } else {
                Map<String, Object> selection = V3Support.map("roomTypeId", content.get("roomTypeId"),
                        "checkInDate", content.get("checkInDate"), "checkOutDate", content.get("checkOutDate"),
                        "roomCount", content.get("roomCount"), "peopleCount", content.get("peopleCount"));
                quote = stayQuote(selection, true);
                if (!expectedFingerprint.equals(quote.fingerprint())) {
                    markQuoteChanged(accountId, operationType, requestKey, digest);
                    return new SubmissionStage(null, false, quote.view());
                }
                PriceLine line = quote.lines().getFirst();
                Map<String, Object> calculation = quote.calculation();
                jdbc.update("""
                        INSERT INTO stay_booking(
                          id,visitor_id,account_id,stay_property_id,stay_property_name_snapshot,room_type_id,
                          room_type_name_snapshot,catalog_version_at_order,check_in_date,check_out_date,nights,
                          room_count,people_count,max_guests_per_room_at_order,unit,unit_amount,total_amount,currency,
                          contact_name,contact_phone,note,status,source_thread_id,source_draft_id,demo_data)
                        VALUES (?,NULL,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING_CONFIRMATION',?,?,true)
                        """, orderId, accountId, quote.parentId(), quote.parentName(), line.resourceId(), line.resourceName(),
                        line.catalogVersion(), content.get("checkInDate"), content.get("checkOutDate"),
                        calculation.get("nights"), calculation.get("roomCount"), calculation.get("peopleCount"),
                        calculation.get("maxGuestsPerRoom"), line.unit(), line.unitAmount(), quote.totalAmount(), "CNY",
                        content.get("contactName"), content.get("contactPhone"), content.get("note"),
                        draft.get("source_thread_id"), draftId);
            }
            int submittedVersion = expectedVersion + 1;
            int updated = jdbc.update("UPDATE " + draftTable + " SET state='SUBMITTED',version=version+1,"
                            + "linked_order_id=?,submitted_at=CURRENT_TIMESTAMP WHERE id=? AND account_id=? AND version=? AND state='DRAFT'",
                    orderId, draftId, accountId, expectedVersion);
            if (updated != 1) {
                throw new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_RESULT_UNAVAILABLE",
                        "草稿提交结果暂时无法确认");
            }
            int finished = jdbc.update("""
                    UPDATE operation_receipt SET state='SUCCEEDED',result_resource_type=?,result_resource_id=?,
                      committed_version=NULL,submitted_draft_id=?,submitted_draft_version=?,committed_at=CURRENT_TIMESTAMP
                    WHERE account_id=? AND operation_type=? AND request_key=? AND state='RESERVED' AND request_digest=?
                    """, resourceType, orderId, draftId, submittedVersion, accountId, operationType, requestKey, digest);
            if (finished != 1) {
                throw new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_RESULT_UNAVAILABLE",
                        "草稿提交结果暂时无法确认");
            }
            return new SubmissionStage(orderId, false, null);
        });
        if (stage == null) {
            throw new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_RESULT_UNAVAILABLE",
                    "草稿提交结果暂时无法确认");
        }
        if (stage.currentQuote() != null) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "QUOTE_CHANGED", "报价已变化，请核对当前金额后重新确认。",
                    V3Support.map("kind", "quote_changed", "expectedQuoteFingerprint", expectedFingerprint,
                            "currentQuote", stage.currentQuote()));
        }
        Map<String, Object> receipt = jdbc.queryForMap("SELECT * FROM operation_receipt WHERE account_id=? AND operation_type=? AND request_key=?",
                accountId, operationType, requestKey);
        Map<String, Object> resource = myOrder("FOOD".equals(type) ? "foods" : "stays", accountId,
                receipt.get("result_resource_id").toString());
        return new CreateResult(writeReceipt(receipt, resource, stage.replayed()), stage.replayed());
    }

    public List<Map<String, Object>> myOrders(String kind, String accountId) {
        return orderRows(kind, " WHERE o.account_id=? ORDER BY o.created_at DESC,o.id DESC", accountId)
                .stream().map(row -> orderView(kind, row, false)).toList();
    }

    public Map<String, Object> myOrder(String kind, String accountId, String id) {
        V3Support.uuid(id, "id");
        Map<String, Object> row = orderRows(kind, " WHERE o.id=? AND o.account_id=?", id, accountId)
                .stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return orderView(kind, row, false);
    }

    public Map<String, Object> cancel(String kind, String accountId, String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "expectedStatus");
        String expected = V3Support.rawRequiredText(body, "expectedStatus");
        String required = switch (kind) {
            case "products" -> "PENDING_PICKUP";
            case "foods" -> "PENDING_VISIT";
            case "stays" -> "PENDING_CONFIRMATION";
            default -> throw invalidKind();
        };
        if (!required.equals(expected)) {
            throw invalidTransition();
        }
        String table = orderTable(kind);
        int updated = jdbc.update("UPDATE " + table
                + " SET status='CANCELLED' WHERE id=? AND account_id=? AND status=?", V3Support.uuid(id, "id"),
                accountId, expected);
        if (updated != 1) {
            myOrder(kind, accountId, id);
            throw invalidTransition();
        }
        return myOrder(kind, accountId, id);
    }

    public List<Map<String, Object>> adminOrders(String kind, String status, String merchantId) {
        List<Object> values = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (status != null) {
            if (!states(kind).contains(status)) {
                V3Support.bad("status 查询参数无效");
            }
            where.append(" AND o.status=?");
            values.add(status);
        }
        if (merchantId != null) {
            V3Support.uuid(merchantId, "merchantId");
            if ("products".equals(kind)) {
                where.append(" AND p.merchant_id=?");
            } else if ("foods".equals(kind)) {
                where.append(" AND o.merchant_id=?");
            } else {
                where.append(" AND s.merchant_id=?");
            }
            values.add(merchantId);
        }
        where.append(" ORDER BY o.created_at DESC,o.id DESC");
        return orderRows(kind, where.toString(), values.toArray()).stream()
                .map(row -> orderView(kind, row, true)).toList();
    }

    public Map<String, Object> adminStatus(String kind, String id, Map<String, Object> body) {
        V3Support.onlyKeys(body, "expectedStatus", "status");
        String expected = V3Support.rawRequiredText(body, "expectedStatus");
        String next = V3Support.rawRequiredText(body, "status");
        if (!validTransition(kind, expected, next)) {
            throw invalidTransition();
        }
        int updated = jdbc.update("UPDATE " + orderTable(kind) + " SET status=? WHERE id=? AND status=?",
                next, V3Support.uuid(id, "id"), expected);
        if (updated != 1) {
            adminOrder(kind, id);
            throw invalidTransition();
        }
        return adminOrder(kind, id);
    }

    public Map<String, Object> writeResult(String accountId, String operationType, String requestKey) {
        V3Support.uuid(requestKey, "requestKey");
        Map<String, Object> receipt = jdbc.queryForList("""
                SELECT * FROM operation_receipt
                WHERE account_id=? AND operation_type=? AND request_key=?
                """, accountId, operationType, requestKey).stream().findFirst().orElse(null);
        if (receipt == null || "RESERVED".equals(receipt.get("state"))) {
            return V3Support.map("requestKey", requestKey, "operationType", operationType,
                    "outcome", "NOT_OBSERVED");
        }
        if ("NOT_APPLIED".equals(receipt.get("state"))) {
            return V3Support.map("requestKey", requestKey, "operationType", operationType,
                    "outcome", "NOT_APPLIED", "terminalReason", receipt.get("terminal_reason"),
                    "terminalAt", V3Support.utc(receipt.get("terminal_at")), "target",
                    V3Support.map("resourceType", receipt.get("requested_resource_type"),
                            "resourceId", receipt.get("requested_resource_id")), "current", null);
        }
        String kind = kindForResource(receipt.get("result_resource_type").toString());
        Map<String, Object> resource = myOrder(kind, accountId, receipt.get("result_resource_id").toString());
        return V3Support.map("requestKey", requestKey, "operationType", operationType, "outcome", "SUCCEEDED",
                "receipt", writeReceipt(receipt, resource, true));
    }

    public Map<String, Object> statusForThread(String accountId, String threadId) {
        V3Support.uuid(threadId, "threadId");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : orderRows("foods", " WHERE o.account_id=? AND o.source_thread_id=? ORDER BY o.created_at DESC",
                accountId, threadId)) {
            result.add(V3Support.map("orderType", "FOOD", "orderId", row.get("id"),
                    "resourceId", row.get("merchant_id"), "resourceName", row.get("merchant_name_snapshot"),
                    "status", row.get("status"), "createdAt", V3Support.utc(row.get("created_at")),
                    "updatedAt", V3Support.utc(row.get("updated_at"))));
        }
        for (Map<String, Object> row : orderRows("stays", " WHERE o.account_id=? AND o.source_thread_id=? ORDER BY o.created_at DESC",
                accountId, threadId)) {
            result.add(V3Support.map("orderType", "STAY", "orderId", row.get("id"),
                    "resourceId", row.get("stay_property_id"), "resourceName", row.get("stay_property_name_snapshot"),
                    "status", row.get("status"), "createdAt", V3Support.utc(row.get("created_at")),
                    "updatedAt", V3Support.utc(row.get("updated_at"))));
        }
        return V3Support.map("threadId", threadId, "orders", result);
    }

    private CreateResult create(String accountId, String requestKey, String operationType, String resourceType,
                                Map<String, Object> requestBody, QuoteSupplier quoteSupplier,
                                OrderWriter writer, String expectedFingerprint) {
        V3Support.uuid(requestKey, "Idempotency-Key");
        String digest = V3Support.sha256(V3Support.map("contractVersion", V3Support.CONTRACT,
                "operationType", operationType, "request", requestBody));
        Map<String, Object> existing = transaction.execute(status -> reserve(accountId, requestKey, operationType,
                resourceType, null, digest));
        if (existing != null && "SUCCEEDED".equals(existing.get("state"))) {
            String kind = kindForResource(resourceType);
            Map<String, Object> resource = myOrder(kind, accountId, existing.get("result_resource_id").toString());
            return new CreateResult(writeReceipt(existing, resource, true), true);
        }
        StageResult stage = transaction.execute(status -> {
            Map<String, Object> locked = jdbc.queryForList("""
                    SELECT * FROM operation_receipt
                    WHERE account_id=? AND operation_type=? AND request_key=? FOR UPDATE
                    """, accountId, operationType, requestKey).stream().findFirst().orElseThrow();
            if (!digest.equals(locked.get("request_digest"))) {
                throw idempotencyConflict();
            }
            if ("SUCCEEDED".equals(locked.get("state"))) {
                return StageResult.replayed(locked);
            }
            Quote quote = quoteSupplier.get();
            if (!expectedFingerprint.equals(quote.fingerprint())) {
                jdbc.update("""
                        UPDATE operation_receipt SET state='NOT_APPLIED',terminal_reason='QUOTE_CHANGED',terminal_at=CURRENT_TIMESTAMP
                        WHERE account_id=? AND operation_type=? AND request_key=? AND state='RESERVED' AND request_digest=?
                        """, accountId, operationType, requestKey, digest);
                return StageResult.quoteChanged(quote.view());
            }
            String resourceId = writer.write(quote);
            int terminal = jdbc.update("""
                    UPDATE operation_receipt
                    SET state='SUCCEEDED',result_resource_type=?,result_resource_id=?,committed_version=NULL,
                        committed_at=CURRENT_TIMESTAMP,terminal_reason=NULL,terminal_at=NULL
                    WHERE account_id=? AND operation_type=? AND request_key=? AND state='RESERVED' AND request_digest=?
                    """, resourceType, resourceId, accountId, operationType, requestKey, digest);
            if (terminal != 1) {
                throw new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_RESULT_UNAVAILABLE",
                        "写入结果暂时无法确认");
            }
            return StageResult.applied(resourceId);
        });
        if (stage == null) {
            throw new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_RESULT_UNAVAILABLE", "写入结果暂时无法确认");
        }
        if (stage.currentQuote() != null) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "QUOTE_CHANGED", "报价已变化，请核对当前金额后重新确认。",
                    V3Support.map("kind", "quote_changed", "expectedQuoteFingerprint", expectedFingerprint,
                            "currentQuote", stage.currentQuote()));
        }
        Map<String, Object> receipt = jdbc.queryForMap("""
                SELECT * FROM operation_receipt WHERE account_id=? AND operation_type=? AND request_key=?
                """, accountId, operationType, requestKey);
        String resourceId = receipt.get("result_resource_id").toString();
        Map<String, Object> resource = myOrder(kindForResource(resourceType), accountId, resourceId);
        boolean replayed = stage.replayed();
        return new CreateResult(writeReceipt(receipt, resource, replayed), replayed);
    }

    private Map<String, Object> reserve(String accountId, String requestKey, String operationType,
                                        String resourceType, String requestedResourceId, String digest) {
        Map<String, Object> existing = jdbc.queryForList("""
                SELECT * FROM operation_receipt
                WHERE account_id=? AND operation_type=? AND request_key=? FOR UPDATE
                """, accountId, operationType, requestKey).stream().findFirst().orElse(null);
        if (existing != null) {
            if (!digest.equals(existing.get("request_digest"))) {
                throw idempotencyConflict();
            }
            if ("NOT_APPLIED".equals(existing.get("state"))) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "OPERATION_NOT_APPLIED",
                        "该请求键已可靠终结且未写入业务结果",
                        V3Support.map("kind", "operation_not_applied", "terminalReason", existing.get("terminal_reason"),
                                "terminalAt", V3Support.utc(existing.get("terminal_at"))));
            }
            return existing;
        }
        jdbc.update("""
                INSERT INTO operation_receipt(
                  account_id,operation_type,request_key,request_digest,requested_resource_type,
                  requested_resource_id,state)
                VALUES (?,?,?,?,?,?,'RESERVED')
                """, accountId, operationType, requestKey, digest, resourceType, requestedResourceId);
        return null;
    }

    private Quote productQuote(Map<String, Object> body, boolean lock) {
        String id = V3Support.uuid(body, "productId");
        int quantity = V3Support.positiveInt(body, "quantity");
        String pickupPoint = V3Support.requiredText(body, "pickupPoint", 1, 160);
        Map<String, Object> row = one("""
                SELECT p.*,m.name merchant_name,m.version merchant_version,m.catalog_status merchant_catalog_status
                FROM product p JOIN merchant m ON m.id=p.merchant_id
                WHERE p.id=? AND p.catalog_status='PUBLISHED' AND m.catalog_status='PUBLISHED'
                """ + (lock ? " FOR UPDATE" : ""), id);
        if (!pickupPoint.equals(row.get("pickup_point"))) {
            V3Support.notFound();
        }
        BigDecimal unitAmount = price(row);
        BigDecimal total = unitAmount.multiply(BigDecimal.valueOf(quantity));
        PriceLine line = new PriceLine(1, "PRODUCT", id, row.get("name").toString(),
                number(row.get("version")), "ITEM", unitAmount, quantity, total, null, bool(row.get("demo_data")));
        Map<String, Object> selection = V3Support.map("productId", id, "quantity", quantity, "pickupPoint", pickupPoint);
        List<Map<String, Object>> inputs = List.of(priceInput(row, "ITEM", null));
        return quote("PRODUCT", List.of(line), selection, inputs, null, null,
                V3Support.map("kind", "PRODUCT", "productId", id, "quantity", quantity, "pickupPoint", pickupPoint));
    }

    private Quote foodQuote(Map<String, Object> body, boolean lock) {
        Map<String, Object> selection = foodSelection(body);
        String merchantId = selection.get("merchantId").toString();
        Map<String, Object> merchant = one("SELECT * FROM merchant WHERE id=? AND catalog_status='PUBLISHED'"
                + (lock ? " FOR UPDATE" : ""), merchantId);
        @SuppressWarnings("unchecked") List<Map<String, Object>> requested = (List<Map<String, Object>>) selection.get("items");
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        requested.stream().map(item -> item.get("foodItemId").toString()).sorted().forEach(id -> {
            Map<String, Object> row = one("""
                    SELECT f.*,m.name merchant_name,m.version merchant_version,m.catalog_status merchant_catalog_status
                    FROM food_item f JOIN merchant m ON m.id=f.merchant_id
                    WHERE f.id=? AND f.catalog_status='PUBLISHED' AND m.catalog_status='PUBLISHED'
                    """ + (lock ? " FOR UPDATE" : ""), id);
            if (!merchantId.equals(row.get("merchant_id"))) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "FOOD_MERCHANT_MISMATCH", "餐食必须来自同一家店铺");
            }
            rows.put(id, row);
        });
        List<PriceLine> lines = new ArrayList<>();
        List<Map<String, Object>> inputs = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (int index = 0; index < requested.size(); index++) {
            Map<String, Object> item = requested.get(index);
            Map<String, Object> row = rows.get(item.get("foodItemId").toString());
            int quantity = (Integer) item.get("quantity");
            BigDecimal amount = price(row);
            BigDecimal lineAmount = amount.multiply(BigDecimal.valueOf(quantity));
            total = total.add(lineAmount);
            lines.add(new PriceLine(index + 1, "FOOD", row.get("id").toString(), row.get("name").toString(),
                    number(row.get("version")), "PORTION", amount, quantity, lineAmount,
                    row.get("item_type").toString(), bool(row.get("demo_data"))));
            inputs.add(priceInput(row, "PORTION", null));
        }
        Map<String, Object> calculation = V3Support.map("kind", "FOOD", "merchantId", merchantId,
                "visitAt", selection.get("visitAt"), "peopleCount", selection.get("peopleCount"));
        return quote("FOOD", lines, selection, inputs, merchantId, merchant.get("name").toString(), calculation);
    }

    private Quote stayQuote(Map<String, Object> body, boolean lock) {
        Map<String, Object> selection = staySelection(body);
        String roomId = selection.get("roomTypeId").toString();
        Map<String, Object> row = one("""
                SELECT r.*,s.id stay_property_id,s.name stay_property_name,s.version stay_property_version,
                       s.catalog_status stay_catalog_status,m.id merchant_id,m.name merchant_name,
                       m.version merchant_version,m.catalog_status merchant_catalog_status
                FROM room_type r JOIN stay_property s ON s.id=r.stay_property_id
                JOIN merchant m ON m.id=s.merchant_id
                WHERE r.id=? AND r.catalog_status='PUBLISHED' AND s.catalog_status='PUBLISHED'
                  AND m.catalog_status='PUBLISHED'
                """ + (lock ? " FOR UPDATE" : ""), roomId);
        int nights = (int) ChronoUnit.DAYS.between((LocalDate) selection.get("checkInDateValue"),
                (LocalDate) selection.get("checkOutDateValue"));
        int roomCount = (Integer) selection.get("roomCount");
        int peopleCount = (Integer) selection.get("peopleCount");
        int capacity;
        try {
            capacity = Math.multiplyExact(number(row.get("max_guests")), roomCount);
        } catch (ArithmeticException exception) {
            V3Support.bad("住宿容量计算溢出");
            return null;
        }
        if (peopleCount > capacity) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "CAPACITY_EXCEEDED", "人数超过演示房型容量");
        }
        BigDecimal unitAmount = price(row);
        int quantity = Math.multiplyExact(nights, roomCount);
        BigDecimal total = unitAmount.multiply(BigDecimal.valueOf(quantity));
        PriceLine line = new PriceLine(1, "STAY", roomId, row.get("name").toString(),
                number(row.get("version")), "ROOM_NIGHT", unitAmount, quantity, total, null,
                bool(row.get("demo_data")));
        Map<String, Object> publicSelection = V3Support.map("roomTypeId", roomId,
                "checkInDate", selection.get("checkInDate"), "checkOutDate", selection.get("checkOutDate"),
                "roomCount", roomCount, "peopleCount", peopleCount);
        Map<String, Object> calculation = V3Support.map("kind", "STAY", "roomTypeId", roomId,
                "checkInDate", selection.get("checkInDate"), "checkOutDate", selection.get("checkOutDate"),
                "nights", nights, "roomCount", roomCount, "peopleCount", peopleCount,
                "maxGuestsPerRoom", number(row.get("max_guests")), "totalCapacity", capacity);
        return quote("STAY", List.of(line), publicSelection, List.of(priceInput(row, "ROOM_NIGHT", row)),
                row.get("stay_property_id").toString(), row.get("stay_property_name").toString(), calculation);
    }

    private Map<String, Object> foodSelection(Map<String, Object> body) {
        String merchantId = V3Support.uuid(body, "merchantId");
        List<Map<String, Object>> items = V3Support.objectList(body, "items", 1, 50);
        Set<String> ids = new HashSet<>();
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> item : items) {
            V3Support.onlyKeys(item, "foodItemId", "quantity");
            String id = V3Support.uuid(item, "foodItemId");
            if (!ids.add(id)) {
                throw new ApiRequestException(HttpStatus.BAD_REQUEST, "DUPLICATE_FOOD_ITEM", "餐食项不能重复");
            }
            normalized.add(V3Support.map("foodItemId", id, "quantity", V3Support.positiveInt(item, "quantity")));
        }
        LocalDateTime visitAt = V3Support.dateTime(body, "visitAt");
        return V3Support.map("merchantId", merchantId, "items", normalized,
                "visitAt", visitAt.toString(), "peopleCount", V3Support.positiveInt(body, "peopleCount"));
    }

    private Map<String, Object> staySelection(Map<String, Object> body) {
        String roomId = V3Support.uuid(body, "roomTypeId");
        LocalDate checkIn = V3Support.date(body, "checkInDate");
        LocalDate checkOut = V3Support.date(body, "checkOutDate");
        if (!checkOut.isAfter(checkIn)) {
            V3Support.bad("离店日期必须晚于入住日期");
        }
        return V3Support.map("roomTypeId", roomId, "checkInDate", checkIn.toString(),
                "checkOutDate", checkOut.toString(), "roomCount", V3Support.positiveInt(body, "roomCount"),
                "peopleCount", V3Support.positiveInt(body, "peopleCount"),
                "checkInDateValue", checkIn, "checkOutDateValue", checkOut);
    }

    private Quote quote(String type, List<PriceLine> lines, Map<String, Object> selection,
                        List<Map<String, Object>> priceInputs, String parentId, String parentName,
                        Map<String, Object> calculation) {
        BigDecimal total = lines.stream().map(PriceLine::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
        String fingerprint = V3Support.sha256(V3Support.map("contractVersion", V3Support.CONTRACT,
                "quoteType", type, "selection", selection, "priceInputs", priceInputs));
        List<Map<String, Object>> lineViews = lines.stream().map(line -> (Map<String, Object>) V3Support.map(
                "sequence", line.sequence(), "resourceType", line.resourceType(), "resourceId", line.resourceId(),
                "resourceName", line.resourceName(), "catalogVersion", line.catalogVersion(), "unit", line.unit(),
                "unitAmount", money(line.unitAmount()), "quantity", line.quantity(),
                "lineAmount", money(line.lineAmount()), "demoData", line.demoData())).toList();
        Map<String, Object> view = V3Support.map("quoteType", type, "currency", "CNY", "pricingKind", "DEMO",
                "lines", lineViews, "calculation", calculation, "totalAmount", money(total),
                "quoteFingerprint", fingerprint, "quotedAt", V3Support.now(), "locksInventory", false,
                "holdsCapacity", false, "notice", V3Support.NOTICE);
        return new Quote(view, lines, total, fingerprint, parentId, parentName, calculation);
    }

    private Map<String, Object> priceInput(Map<String, Object> row, String unit, Map<String, Object> stayRow) {
        LinkedHashMap<String, Object> input = V3Support.map("merchantId", row.get("merchant_id"),
                "merchantVersion", number(row.get("merchant_version")));
        if (stayRow != null) {
            input.put("stayPropertyId", row.get("stay_property_id"));
            input.put("stayPropertyVersion", number(row.get("stay_property_version")));
        }
        input.put("resourceId", row.get("id"));
        input.put("catalogVersion", number(row.get("version")));
        input.put("amount", V3Support.money(row.get("price")));
        input.put("currency", "CNY");
        input.put("unit", unit);
        return input;
    }

    private BigDecimal price(Map<String, Object> row) {
        if (row.get("price") == null || new BigDecimal(row.get("price").toString()).signum() <= 0) {
            throw new ApiRequestException(HttpStatus.CONFLICT, "PRICE_UNAVAILABLE", "当前资源没有可用演示价");
        }
        return new BigDecimal(row.get("price").toString()).setScale(2, RoundingMode.UNNECESSARY);
    }

    private Contact contact(Map<String, Object> body) {
        return new Contact(V3Support.requiredText(body, "contactName", 1, 80), V3Support.phone(body),
                V3Support.optionalText(body, "note", 500));
    }

    private Map<String, Object> adminOrder(String kind, String id) {
        Map<String, Object> row = orderRows(kind, " WHERE o.id=?", V3Support.uuid(id, "id"))
                .stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return orderView(kind, row, true);
    }

    private List<Map<String, Object>> orderRows(String kind, String tail, Object... values) {
        String select = switch (kind) {
            case "products" -> "SELECT o.*,p.merchant_id FROM product_order o JOIN product p ON p.id=o.product_id";
            case "foods" -> "SELECT o.* FROM food_order o";
            case "stays" -> "SELECT o.*,s.merchant_id FROM stay_booking o JOIN stay_property s ON s.id=o.stay_property_id";
            default -> throw invalidKind();
        };
        return jdbc.queryForList(select + tail, values);
    }

    private Map<String, Object> orderView(String kind, Map<String, Object> row, boolean admin) {
        LinkedHashMap<String, Object> view = switch (kind) {
            case "products" -> V3Support.map("id", row.get("id"), "orderType", "PRODUCT",
                    "productId", row.get("product_id"), "productName", row.get("product_name_snapshot"),
                    "catalogVersionAtOrder", row.get("catalog_version_at_order"), "quantity", row.get("quantity"),
                    "pickupPoint", row.get("pickup_point"), "unit", row.get("unit"),
                    "unitAmount", V3Support.money(row.get("unit_amount")), "totalAmount", V3Support.money(row.get("total_amount")),
                    "currency", row.get("currency"), "note", row.get("note"), "status", row.get("status"),
                    "demoData", bool(row.get("demo_data")), "createdAt", V3Support.utc(row.get("created_at")),
                    "updatedAt", V3Support.utc(row.get("updated_at")));
            case "foods" -> V3Support.map("id", row.get("id"), "orderType", "FOOD",
                    "merchantId", row.get("merchant_id"), "merchantName", row.get("merchant_name_snapshot"),
                    "items", foodOrderItems(row.get("id").toString()), "visitAt", localDateTime(row.get("visit_at")),
                    "peopleCount", row.get("people_count"), "totalAmount", V3Support.money(row.get("total_amount")),
                    "currency", row.get("currency"), "note", row.get("note"), "status", row.get("status"),
                    "demoData", bool(row.get("demo_data")), "createdAt", V3Support.utc(row.get("created_at")),
                    "updatedAt", V3Support.utc(row.get("updated_at")));
            case "stays" -> V3Support.map("id", row.get("id"), "orderType", "STAY",
                    "stayPropertyId", row.get("stay_property_id"), "stayPropertyName", row.get("stay_property_name_snapshot"),
                    "roomTypeId", row.get("room_type_id"), "roomTypeName", row.get("room_type_name_snapshot"),
                    "catalogVersionAtOrder", row.get("catalog_version_at_order"),
                    "checkInDate", row.get("check_in_date").toString(), "checkOutDate", row.get("check_out_date").toString(),
                    "nights", row.get("nights"), "roomCount", row.get("room_count"),
                    "peopleCount", row.get("people_count"), "maxGuestsPerRoomAtOrder", row.get("max_guests_per_room_at_order"),
                    "unit", row.get("unit"), "unitAmount", V3Support.money(row.get("unit_amount")),
                    "totalAmount", V3Support.money(row.get("total_amount")), "currency", row.get("currency"),
                    "note", row.get("note"), "status", row.get("status"), "demoData", bool(row.get("demo_data")),
                    "createdAt", V3Support.utc(row.get("created_at")), "updatedAt", V3Support.utc(row.get("updated_at")));
            default -> throw invalidKind();
        };
        if (admin) {
            view.put("accountId", row.get("account_id"));
            view.put("contactName", row.get("contact_name"));
            view.put("contactPhone", row.get("contact_phone"));
            view.put("sourceThreadId", row.get("source_thread_id"));
            view.put("sourceDraftId", row.get("source_draft_id"));
        }
        return view;
    }

    private List<Map<String, Object>> foodOrderItems(String orderId) {
        return jdbc.queryForList("SELECT * FROM food_order_item WHERE food_order_id=? ORDER BY sequence_no", orderId)
                .stream().map(row -> (Map<String, Object>) V3Support.map("sequence", row.get("sequence_no"),
                        "foodItemId", row.get("food_item_id"), "foodItemName", row.get("food_item_name_snapshot"),
                        "itemType", row.get("item_type_snapshot"), "catalogVersionAtOrder", row.get("catalog_version_at_order"),
                        "unit", row.get("unit"), "unitAmount", V3Support.money(row.get("unit_amount")),
                        "quantity", row.get("quantity"), "lineAmount", V3Support.money(row.get("line_amount")),
                        "demoData", bool(row.get("demo_data")))).toList();
    }

    private Map<String, Object> writeReceipt(Map<String, Object> receipt, Map<String, Object> resource,
                                             boolean replayed) {
        Object submittedDraft = receipt.get("submitted_draft_id") == null ? null : V3Support.map(
                "id", receipt.get("submitted_draft_id"), "committedVersion", receipt.get("submitted_draft_version"),
                "state", "SUBMITTED");
        return V3Support.map("requestKey", receipt.get("request_key"), "operationType", receipt.get("operation_type"),
                "resourceType", receipt.get("result_resource_type"), "resourceId", receipt.get("result_resource_id"),
                "committedVersion", receipt.get("committed_version"), "committedAt", V3Support.utc(receipt.get("committed_at")),
                "replayed", replayed, "resource", resource, "submittedDraft", submittedDraft);
    }

    private Map<String, Object> readJson(Object value) {
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_RESULT_UNAVAILABLE",
                    "草稿内容暂时无法读取");
        }
    }

    private static List<String> draftMissing(String type, Map<String, Object> content) {
        List<String> order = "FOOD".equals(type)
                ? List.of("merchantId", "items", "visitAt", "peopleCount", "contactName", "contactPhone")
                : List.of("roomTypeId", "checkInDate", "checkOutDate", "roomCount", "peopleCount",
                "contactName", "contactPhone");
        List<String> result = new ArrayList<>();
        for (String field : order) {
            Object value = content.get(field);
            if (value == null || value instanceof String text && text.isBlank()
                    || value instanceof List<?> list && list.isEmpty()) {
                result.add(field);
            }
        }
        return result;
    }

    private void markQuoteChanged(String accountId, String operationType, String requestKey, String digest) {
        int updated = jdbc.update("""
                UPDATE operation_receipt SET state='NOT_APPLIED',terminal_reason='QUOTE_CHANGED',terminal_at=CURRENT_TIMESTAMP
                WHERE account_id=? AND operation_type=? AND request_key=? AND state='RESERVED' AND request_digest=?
                """, accountId, operationType, requestKey, digest);
        if (updated != 1) {
            throw new ApiRequestException(HttpStatus.SERVICE_UNAVAILABLE, "WRITE_RESULT_UNAVAILABLE",
                    "报价变化结果暂时无法确认");
        }
    }

    private Map<String, Object> one(String sql, String id) {
        Map<String, Object> row = jdbc.queryForList(sql, id).stream().findFirst().orElse(null);
        if (row == null) {
            V3Support.notFound();
        }
        return row;
    }

    private static boolean validTransition(String kind, String from, String to) {
        return switch (kind) {
            case "products" -> "PENDING_PICKUP".equals(from) && Set.of("PICKED_UP", "CANCELLED").contains(to);
            case "foods" -> "PENDING_VISIT".equals(from) && Set.of("COMPLETED", "CANCELLED").contains(to);
            case "stays" -> ("PENDING_CONFIRMATION".equals(from) && Set.of("CONFIRMED", "CANCELLED").contains(to))
                    || ("CONFIRMED".equals(from) && Set.of("COMPLETED", "CANCELLED").contains(to));
            default -> false;
        };
    }

    private static Set<String> states(String kind) {
        return switch (kind) {
            case "products" -> Set.of("PENDING_PICKUP", "PICKED_UP", "CANCELLED");
            case "foods" -> Set.of("PENDING_VISIT", "COMPLETED", "CANCELLED");
            case "stays" -> Set.of("PENDING_CONFIRMATION", "CONFIRMED", "COMPLETED", "CANCELLED");
            default -> throw invalidKind();
        };
    }

    private static String orderTable(String kind) {
        return switch (kind) {
            case "products" -> "product_order";
            case "foods" -> "food_order";
            case "stays" -> "stay_booking";
            default -> throw invalidKind();
        };
    }

    private static String kindForResource(String resourceType) {
        return switch (resourceType) {
            case "PRODUCT_ORDER" -> "products";
            case "FOOD_ORDER" -> "foods";
            case "STAY_BOOKING" -> "stays";
            default -> throw invalidKind();
        };
    }

    private static ApiRequestException invalidKind() {
        return new ApiRequestException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "订单类型不存在");
    }

    private static ApiRequestException invalidTransition() {
        return new ApiRequestException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "订单状态已变化或不允许该操作");
    }

    private static ApiRequestException idempotencyConflict() {
        return new ApiRequestException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "请求键已绑定其他请求内容");
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : ((Number) value).intValue() != 0;
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String localDateTime(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().withNano(0).toString();
        }
        return value.toString().replace(' ', 'T');
    }

    public record CreateResult(Map<String, Object> receipt, boolean replayed) {
    }

    private record Contact(String name, String phone, String note) {
    }

    private record PriceLine(int sequence, String resourceType, String resourceId, String resourceName,
                             int catalogVersion, String unit, BigDecimal unitAmount, int quantity,
                             BigDecimal lineAmount, String itemType, boolean demoData) {
    }

    private record Quote(Map<String, Object> view, List<PriceLine> lines, BigDecimal totalAmount,
                         String fingerprint, String parentId, String parentName,
                         Map<String, Object> calculation) {
    }

    private record StageResult(String resourceId, boolean replayed, Map<String, Object> currentQuote) {
        static StageResult applied(String resourceId) {
            return new StageResult(resourceId, false, null);
        }

        static StageResult replayed(Map<String, Object> receipt) {
            return new StageResult(receipt.get("result_resource_id").toString(), true, null);
        }

        static StageResult quoteChanged(Map<String, Object> quote) {
            return new StageResult(null, false, quote);
        }
    }

    private record SubmissionStage(String resourceId, boolean replayed, Map<String, Object> currentQuote) {
    }

    @FunctionalInterface
    private interface QuoteSupplier {
        Quote get();
    }

    @FunctionalInterface
    private interface OrderWriter {
        String write(Quote quote);
    }
}
