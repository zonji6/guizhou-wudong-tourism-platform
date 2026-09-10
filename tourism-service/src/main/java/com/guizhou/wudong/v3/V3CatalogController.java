package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class V3CatalogController {
    private final V3CatalogService catalogService;

    public V3CatalogController(V3CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/api/products")
    ApiEnvelope<List<Map<String, Object>>> products(
            @RequestParam(required = false) String categoryTag,
            @RequestParam(name = "tag", required = false, defaultValue = "") List<String> tags) {
        return ApiEnvelope.ok(catalogService.products(categoryTag, cleanTags(tags)));
    }

    @GetMapping("/api/products/{id}")
    ApiEnvelope<Map<String, Object>> product(@PathVariable String id) {
        return ApiEnvelope.ok(catalogService.product(id));
    }

    @GetMapping("/api/food-merchants")
    ApiEnvelope<List<Map<String, Object>>> foodMerchants(
            @RequestParam(name = "tag", required = false, defaultValue = "") List<String> tags) {
        return ApiEnvelope.ok(catalogService.foodMerchants(cleanTags(tags)));
    }

    @GetMapping("/api/food-merchants/{id}")
    ApiEnvelope<Map<String, Object>> foodMerchant(@PathVariable String id) {
        return ApiEnvelope.ok(catalogService.foodMerchant(id));
    }

    @GetMapping("/api/foods")
    ApiEnvelope<List<Map<String, Object>>> foods(
            @RequestParam String merchantId,
            @RequestParam(required = false) String categoryTag,
            @RequestParam(name = "tag", required = false, defaultValue = "") List<String> tags) {
        return ApiEnvelope.ok(catalogService.foods(merchantId, categoryTag, cleanTags(tags)));
    }

    @GetMapping("/api/foods/{id}")
    ApiEnvelope<Map<String, Object>> food(@PathVariable String id) {
        return ApiEnvelope.ok(catalogService.food(id));
    }

    @GetMapping("/api/stays")
    ApiEnvelope<List<Map<String, Object>>> stays(
            @RequestParam(required = false) Integer peopleCount,
            @RequestParam(required = false) String roomTypeId,
            @RequestParam(name = "tag", required = false, defaultValue = "") List<String> tags) {
        return ApiEnvelope.ok(catalogService.stays(peopleCount, roomTypeId, cleanTags(tags)));
    }

    @GetMapping("/api/stays/{id}")
    ApiEnvelope<Map<String, Object>> stay(@PathVariable String id) {
        return ApiEnvelope.ok(catalogService.stay(id));
    }

    @GetMapping("/api/room-types/{id}")
    ApiEnvelope<Map<String, Object>> room(@PathVariable String id) {
        return ApiEnvelope.ok(catalogService.room(id));
    }

    @GetMapping("/api/places")
    ApiEnvelope<Map<String, Object>> places(
            @RequestParam(required = false) String category,
            @RequestParam(name = "tag", required = false, defaultValue = "") List<String> tags) {
        return ApiEnvelope.ok(catalogService.places(category, cleanTags(tags)));
    }

    @GetMapping("/api/places/{id}")
    ApiEnvelope<Map<String, Object>> place(@PathVariable String id) {
        return ApiEnvelope.ok(catalogService.place(id));
    }

    @GetMapping("/api/admin/{kind:merchants|products|foods|stays|room-types|places}")
    ApiEnvelope<List<Map<String, Object>>> adminList(@PathVariable String kind) {
        V3Support.principal("ADMIN");
        return ApiEnvelope.ok(catalogService.adminList(kind));
    }

    @GetMapping("/api/admin/{kind:merchants|products|foods|stays|room-types|places}/{id}")
    ApiEnvelope<Map<String, Object>> adminGet(@PathVariable String kind, @PathVariable String id) {
        V3Support.principal("ADMIN");
        return ApiEnvelope.ok(catalogService.adminGet(kind, id));
    }

    @PostMapping("/api/admin/{kind:merchants|products|foods|stays|room-types|places}")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> adminCreate(@PathVariable String kind,
                                                                 @RequestBody Map<String, Object> body) {
        V3Support.principal("ADMIN");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(catalogService.adminCreate(kind, body)));
    }

    @PatchMapping("/api/admin/{kind:merchants|products|foods|stays|room-types|places}/{id}")
    ApiEnvelope<Map<String, Object>> adminPatch(@PathVariable String kind, @PathVariable String id,
                                                @RequestBody Map<String, Object> body) {
        V3Support.principal("ADMIN");
        return ApiEnvelope.ok(catalogService.adminPatch(kind, id, body));
    }

    @PatchMapping("/api/admin/{kind:merchants|products|foods|stays|room-types|places}/{id}/catalog-status")
    ApiEnvelope<Map<String, Object>> adminStatus(@PathVariable String kind, @PathVariable String id,
                                                 @RequestBody Map<String, Object> body) {
        V3Support.principal("ADMIN");
        return ApiEnvelope.ok(catalogService.adminStatus(kind, id, body));
    }

    private static List<String> cleanTags(List<String> tags) {
        if (tags == null || tags.size() == 1 && tags.getFirst().isEmpty()) {
            return List.of();
        }
        if (tags.stream().anyMatch(String::isBlank) || tags.size() != tags.stream().distinct().count()) {
            V3Support.bad("tag 查询参数包含空值或重复值");
        }
        return List.copyOf(tags);
    }
}
