package com.guizhou.wudong.api;

import com.guizhou.wudong.service.InternalCatalogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Set;

@RestController
@Profile("legacy-v2")
@RequestMapping("/internal/agent")
public class InternalCatalogController {
    private static final Set<String> BASIC_QUERY = Set.of("keywords", "tag");
    private static final Set<String> STAY_QUERY = Set.of("keywords", "tag", "peopleCount", "roomTypeId");
    private static final Set<String> REPEATABLE_QUERY = Set.of("tag");

    private final InternalCatalogService internalCatalogService;

    public InternalCatalogController(InternalCatalogService internalCatalogService) {
        this.internalCatalogService = internalCatalogService;
    }

    @GetMapping("/products/search")
    ApiEnvelope<List<CatalogViews.ProductView>> products(
            @RequestParam(required = false) String keywords, HttpServletRequest request) {
        rejectUnknownOrRepeatedQuery(request, BASIC_QUERY);
        return ApiEnvelope.ok(internalCatalogService.products(keywords, repeatedTags(request)));
    }

    @GetMapping("/foods/search")
    ApiEnvelope<List<CatalogViews.FoodView>> foods(
            @RequestParam(required = false) String keywords, HttpServletRequest request) {
        rejectUnknownOrRepeatedQuery(request, BASIC_QUERY);
        return ApiEnvelope.ok(internalCatalogService.foods(keywords, repeatedTags(request)));
    }

    @GetMapping("/stays/search")
    ApiEnvelope<List<CatalogViews.StayView>> stays(
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) Integer peopleCount,
            @RequestParam(required = false) String roomTypeId,
            HttpServletRequest request) {
        rejectUnknownOrRepeatedQuery(request, STAY_QUERY);
        return ApiEnvelope.ok(internalCatalogService.stays(
                keywords, repeatedTags(request), peopleCount, roomTypeId));
    }

    @GetMapping("/places/search")
    ApiEnvelope<CatalogViews.MapPlacesView> places(
            @RequestParam(required = false) String keywords, HttpServletRequest request) {
        rejectUnknownOrRepeatedQuery(request, BASIC_QUERY);
        return ApiEnvelope.ok(internalCatalogService.places(keywords, repeatedTags(request)));
    }

    private static List<String> repeatedTags(HttpServletRequest request) {
        String[] values = request.getParameterValues("tag");
        return values == null ? null : List.of(values);
    }

    private static void rejectUnknownOrRepeatedQuery(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet())) {
            throw new IllegalArgumentException("存在不支持的查询参数");
        }
        for (String parameter : allowed) {
            if (REPEATABLE_QUERY.contains(parameter)) {
                continue;
            }
            String[] values = request.getParameterValues(parameter);
            if (values != null && values.length != 1) {
                throw new IllegalArgumentException("查询参数只能提供一次");
            }
        }
    }
}
