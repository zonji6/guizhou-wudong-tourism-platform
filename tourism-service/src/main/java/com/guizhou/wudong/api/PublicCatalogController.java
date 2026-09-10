package com.guizhou.wudong.api;

import com.guizhou.wudong.service.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Set;

@RestController
@Profile("legacy-v2")
@RequestMapping("/api")
public class PublicCatalogController {
    private final CatalogService catalogService;

    public PublicCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/products")
    ApiEnvelope<List<CatalogViews.ProductView>> products(
            @RequestParam(required = false) String categoryTag,
            HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of("categoryTag", "tag"));
        return ApiEnvelope.ok(catalogService.publicProducts(categoryTag, repeatedTags(request)));
    }

    @GetMapping("/products/{id}")
    ApiEnvelope<CatalogViews.ProductView> product(@PathVariable String id, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of());
        return ApiEnvelope.ok(catalogService.publicProduct(id));
    }

    @GetMapping("/foods")
    ApiEnvelope<List<CatalogViews.FoodView>> foods(
            @RequestParam(required = false) String categoryTag,
            HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of("categoryTag", "tag"));
        return ApiEnvelope.ok(catalogService.publicFoods(categoryTag, repeatedTags(request)));
    }

    @GetMapping("/foods/{id}")
    ApiEnvelope<CatalogViews.FoodView> food(@PathVariable String id, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of());
        return ApiEnvelope.ok(catalogService.publicFood(id));
    }

    @GetMapping("/stays")
    ApiEnvelope<List<CatalogViews.StayView>> stays(
            @RequestParam(required = false) Integer peopleCount,
            @RequestParam(required = false) String roomTypeId,
            HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of("peopleCount", "roomTypeId", "tag"));
        return ApiEnvelope.ok(catalogService.publicStays(peopleCount, roomTypeId, repeatedTags(request)));
    }

    @GetMapping("/stays/{id}")
    ApiEnvelope<CatalogViews.StayView> stay(@PathVariable String id, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of());
        return ApiEnvelope.ok(catalogService.publicStay(id));
    }

    @GetMapping("/room-types/{id}")
    ApiEnvelope<CatalogViews.RoomTypeView> roomType(@PathVariable String id, HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of());
        return ApiEnvelope.ok(catalogService.publicRoomType(id));
    }

    @GetMapping("/places")
    ApiEnvelope<CatalogViews.MapPlacesView> places(
            @RequestParam(required = false) String category,
            HttpServletRequest request) {
        rejectUnknownQueryParameters(request, Set.of("category", "tag"));
        return ApiEnvelope.ok(catalogService.publicPlaces(category, repeatedTags(request)));
    }

    private static List<String> repeatedTags(HttpServletRequest request) {
        String[] values = request.getParameterValues("tag");
        return values == null ? null : List.of(values);
    }

    private static void rejectUnknownQueryParameters(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet())) {
            throw new IllegalArgumentException("存在不支持的查询参数");
        }
    }
}
