package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class V3OrderController {
    private final V3OrderService orderService;

    public V3OrderController(V3OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/order-quotes/product")
    ApiEnvelope<Map<String, Object>> productQuote(@RequestBody Map<String, Object> body) {
        V3Support.principal("USER");
        return ApiEnvelope.ok(orderService.quoteProduct(body));
    }

    @PostMapping("/api/order-quotes/food")
    ApiEnvelope<Map<String, Object>> foodQuote(@RequestBody Map<String, Object> body) {
        V3Support.principal("USER");
        return ApiEnvelope.ok(orderService.quoteFood(body));
    }

    @PostMapping("/api/order-quotes/stay")
    ApiEnvelope<Map<String, Object>> stayQuote(@RequestBody Map<String, Object> body) {
        V3Support.principal("USER");
        return ApiEnvelope.ok(orderService.quoteStay(body));
    }

    @PostMapping("/api/product-orders")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> createProduct(
            @RequestHeader("Idempotency-Key") String requestKey, @RequestBody Map<String, Object> body) {
        V3OrderService.CreateResult result = orderService.createProduct(
                V3Support.principal("USER").accountId(), requestKey, body);
        return response(result);
    }

    @PostMapping("/api/food-orders")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> createFood(
            @RequestHeader("Idempotency-Key") String requestKey, @RequestBody Map<String, Object> body) {
        V3OrderService.CreateResult result = orderService.createFood(
                V3Support.principal("USER").accountId(), requestKey, body);
        return response(result);
    }

    @PostMapping("/api/stay-bookings")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> createStay(
            @RequestHeader("Idempotency-Key") String requestKey, @RequestBody Map<String, Object> body) {
        V3OrderService.CreateResult result = orderService.createStay(
                V3Support.principal("USER").accountId(), requestKey, body);
        return response(result);
    }

    @GetMapping("/api/me/{kind:product-orders|food-orders|stay-bookings}")
    ApiEnvelope<List<Map<String, Object>>> myOrders(@PathVariable String kind) {
        return ApiEnvelope.ok(orderService.myOrders(toKind(kind), V3Support.principal("USER").accountId()));
    }

    @GetMapping("/api/me/{kind:product-orders|food-orders|stay-bookings}/{id}")
    ApiEnvelope<Map<String, Object>> myOrder(@PathVariable String kind, @PathVariable String id) {
        return ApiEnvelope.ok(orderService.myOrder(toKind(kind), V3Support.principal("USER").accountId(), id));
    }

    @PostMapping("/api/me/{kind:product-orders|food-orders|stay-bookings}/{id}/cancel")
    ApiEnvelope<Map<String, Object>> cancel(@PathVariable String kind, @PathVariable String id,
                                            @RequestBody Map<String, Object> body) {
        return ApiEnvelope.ok(orderService.cancel(toKind(kind), V3Support.principal("USER").accountId(), id, body));
    }

    @PostMapping("/api/me/{kind:food-drafts|stay-drafts}/{id}/submit")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> submitDraft(
            @PathVariable String kind, @PathVariable String id,
            @RequestHeader("Idempotency-Key") String requestKey, @RequestBody Map<String, Object> body) {
        V3OrderService.CreateResult result = orderService.submitDraft(
                "food-drafts".equals(kind) ? "FOOD" : "STAY",
                V3Support.principal("USER").accountId(), id, requestKey, body);
        return response(result);
    }

    @GetMapping("/api/admin/orders/{kind:products|foods|stays}")
    ApiEnvelope<List<Map<String, Object>>> adminOrders(@PathVariable String kind,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String merchantId) {
        V3Support.principal("ADMIN");
        return ApiEnvelope.ok(orderService.adminOrders(kind, status, merchantId));
    }

    @PostMapping("/api/admin/orders/{kind:products|foods|stays}/{id}/status")
    ApiEnvelope<Map<String, Object>> adminStatus(@PathVariable String kind, @PathVariable String id,
                                                 @RequestBody Map<String, Object> body) {
        V3Support.principal("ADMIN");
        return ApiEnvelope.ok(orderService.adminStatus(kind, id, body));
    }

    @GetMapping("/api/me/write-results/{operationType}/{requestKey}")
    ApiEnvelope<Map<String, Object>> writeResult(@PathVariable String operationType,
                                                 @PathVariable String requestKey) {
        return ApiEnvelope.ok(orderService.writeResult(V3Support.principal("USER").accountId(),
                operationType, requestKey));
    }

    private static ResponseEntity<ApiEnvelope<Map<String, Object>>> response(V3OrderService.CreateResult result) {
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiEnvelope.ok(result.receipt()));
    }

    private static String toKind(String routeKind) {
        return switch (routeKind) {
            case "product-orders" -> "products";
            case "food-orders" -> "foods";
            case "stay-bookings" -> "stays";
            default -> throw new IllegalArgumentException("订单类型无效");
        };
    }
}
