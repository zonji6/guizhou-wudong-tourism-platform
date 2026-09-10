package com.guizhou.wudong.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.guizhou.wudong.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

import java.util.List;

@RestController
@Profile("legacy-v2")
@RequestMapping("/api")
public class PublicOrderController {
    private final OrderService orderService;

    public PublicOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/product-orders")
    ApiEnvelope<OrderViews.ProductPublicView> createProduct(
            @RequestBody JsonNode body, HttpServletRequest request) {
        rejectQueryParameters(request);
        return ApiEnvelope.ok(orderService.createProduct(VisitorIdentity.require(request), OrderRequests.product(body)));
    }

    @PostMapping("/food-orders")
    ApiEnvelope<OrderViews.FoodPublicView> createFood(@RequestBody JsonNode body, HttpServletRequest request) {
        rejectQueryParameters(request);
        return ApiEnvelope.ok(orderService.createFood(VisitorIdentity.require(request), OrderRequests.food(body)));
    }

    @PostMapping("/stay-bookings")
    ApiEnvelope<OrderViews.StayPublicView> createStay(@RequestBody JsonNode body, HttpServletRequest request) {
        rejectQueryParameters(request);
        return ApiEnvelope.ok(orderService.createStay(VisitorIdentity.require(request), OrderRequests.stay(body)));
    }

    @GetMapping("/me/product-orders")
    ApiEnvelope<List<OrderViews.ProductPublicView>> myProducts(HttpServletRequest request) {
        rejectQueryParameters(request);
        return ApiEnvelope.ok(orderService.myProducts(VisitorIdentity.require(request)));
    }

    @GetMapping("/me/food-orders")
    ApiEnvelope<List<OrderViews.FoodPublicView>> myFoods(HttpServletRequest request) {
        rejectQueryParameters(request);
        return ApiEnvelope.ok(orderService.myFoods(VisitorIdentity.require(request)));
    }

    @GetMapping("/me/stay-bookings")
    ApiEnvelope<List<OrderViews.StayPublicView>> myStays(HttpServletRequest request) {
        rejectQueryParameters(request);
        return ApiEnvelope.ok(orderService.myStays(VisitorIdentity.require(request)));
    }

    private static void rejectQueryParameters(HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            throw new IllegalArgumentException("存在不支持的查询参数");
        }
    }
}
