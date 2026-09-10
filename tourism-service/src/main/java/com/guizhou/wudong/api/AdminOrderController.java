package com.guizhou.wudong.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.guizhou.wudong.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Set;

@RestController
@Profile("legacy-v2")
@RequestMapping("/api/admin/orders")
public class AdminOrderController {
    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/products")
    ApiEnvelope<List<OrderViews.ProductAdminView>> products(
            @RequestParam(required = false) String status, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of("status"));
        return ApiEnvelope.ok(orderService.adminProducts(status));
    }

    @PatchMapping("/products/{id}/status")
    ApiEnvelope<OrderViews.ProductAdminView> productStatus(
            @PathVariable String id, @RequestBody JsonNode body, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of());
        return ApiEnvelope.ok(orderService.updateProductStatus(id, OrderRequests.status(body)));
    }

    @GetMapping("/foods")
    ApiEnvelope<List<OrderViews.FoodAdminView>> foods(
            @RequestParam(required = false) String status, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of("status"));
        return ApiEnvelope.ok(orderService.adminFoods(status));
    }

    @PatchMapping("/foods/{id}/status")
    ApiEnvelope<OrderViews.FoodAdminView> foodStatus(
            @PathVariable String id, @RequestBody JsonNode body, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of());
        return ApiEnvelope.ok(orderService.updateFoodStatus(id, OrderRequests.status(body)));
    }

    @GetMapping("/stays")
    ApiEnvelope<List<OrderViews.StayAdminView>> stays(
            @RequestParam(required = false) String status, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of("status"));
        return ApiEnvelope.ok(orderService.adminStays(status));
    }

    @PatchMapping("/stays/{id}/status")
    ApiEnvelope<OrderViews.StayAdminView> stayStatus(
            @PathVariable String id, @RequestBody JsonNode body, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of());
        return ApiEnvelope.ok(orderService.updateStayStatus(id, OrderRequests.status(body)));
    }

    private static void rejectUnknownQueryParameters(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet())) {
            throw new IllegalArgumentException("存在不支持的查询参数");
        }
        String[] statuses = request.getParameterValues("status");
        if (statuses != null && statuses.length != 1) {
            throw new IllegalArgumentException("订单状态筛选只能提供一次");
        }
    }
}
