package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiEnvelope;
import com.guizhou.wudong.api.ApiRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class V3InternalController {
    private final V3CatalogService catalogService;
    private final V3CommunityService communityService;
    private final V3KnowledgeService knowledgeService;
    private final V3PersonalService personalService;
    private final V3OrderService orderService;
    private final V3IdentityService identityService;

    public V3InternalController(V3CatalogService catalogService, V3CommunityService communityService,
                                V3KnowledgeService knowledgeService, V3PersonalService personalService,
                                V3OrderService orderService, V3IdentityService identityService) {
        this.catalogService = catalogService;
        this.communityService = communityService;
        this.knowledgeService = knowledgeService;
        this.personalService = personalService;
        this.orderService = orderService;
        this.identityService = identityService;
    }

    @GetMapping("/internal/agent/products/search")
    ApiEnvelope<List<Map<String, Object>>> products(@RequestParam String keywords,
                                                    @RequestParam(required = false) Integer limit) {
        return ApiEnvelope.ok(search(catalogService.products(null, List.of()), keywords, limit));
    }

    @GetMapping("/internal/agent/foods/search")
    ApiEnvelope<List<Map<String, Object>>> foods(@RequestParam String keywords,
                                                 @RequestParam(required = false) Integer limit) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Map<String, Object> merchant : catalogService.foodMerchants(List.of())) {
            values.addAll(catalogService.foods(merchant.get("id").toString(), null, List.of()));
        }
        return ApiEnvelope.ok(search(values, keywords, limit));
    }

    @GetMapping("/internal/agent/stays/search")
    ApiEnvelope<List<Map<String, Object>>> stays(@RequestParam String keywords,
                                                 @RequestParam(required = false) Integer limit) {
        return ApiEnvelope.ok(search(catalogService.stays(null, null, List.of()), keywords, limit));
    }

    @GetMapping("/internal/agent/places/search")
    ApiEnvelope<List<Map<String, Object>>> places(@RequestParam String keywords,
                                                  @RequestParam(required = false) Integer limit) {
        @SuppressWarnings("unchecked") List<Map<String, Object>> places =
                (List<Map<String, Object>>) catalogService.places(null, List.of()).get("places");
        return ApiEnvelope.ok(search(places, keywords, limit));
    }

    @GetMapping("/internal/agent/route-guides/search")
    ApiEnvelope<List<Map<String, Object>>> routes(@RequestParam String keywords,
                                                  @RequestParam(required = false) Integer limit) {
        return ApiEnvelope.ok(search(communityService.list("ROUTE_GUIDE", null), keywords, limit));
    }

    @GetMapping("/internal/agent/knowledge/search")
    ApiEnvelope<Map<String, Object>> knowledge(@RequestParam String keywords,
                                               @RequestParam(required = false) Integer limit) {
        return ApiEnvelope.ok(knowledgeService.keywordSearch(keywords, limit));
    }

    @GetMapping("/internal/agent/knowledge/active-builds")
    ApiEnvelope<Map<String, Object>> activeBuilds(@RequestParam String configHash) {
        return ApiEnvelope.ok(knowledgeService.activeBuilds(configHash));
    }

    @PostMapping("/internal/agent/knowledge/eligibility")
    ApiEnvelope<Map<String, Object>> eligibility(@RequestBody Map<String, Object> body) {
        return ApiEnvelope.ok(knowledgeService.eligibility(body));
    }

    @GetMapping("/internal/agent/knowledge-build-inputs/{taskId}")
    ApiEnvelope<Map<String, Object>> buildInputs(@PathVariable String taskId) {
        return ApiEnvelope.ok(knowledgeService.buildInputs(taskId));
    }

    @PostMapping("/internal/agent/run-summaries")
    ApiEnvelope<Map<String, Object>> runSummaries(@RequestBody Map<String, Object> body) {
        return ApiEnvelope.ok(knowledgeService.recordRunSummary(body));
    }

    @GetMapping("/internal/agent/itineraries/{id}")
    ApiEnvelope<Map<String, Object>> itinerary(@PathVariable String id,
                                               @RequestHeader("X-Wudong-User-Proof") String proof) {
        V3Support.V3Principal principal = userProof(proof);
        return ApiEnvelope.ok(personalService.internalProjection("ITINERARY", principal.accountId(), id));
    }

    @GetMapping("/internal/agent/food-drafts/{id}")
    ApiEnvelope<Map<String, Object>> foodDraft(@PathVariable String id,
                                               @RequestHeader("X-Wudong-User-Proof") String proof) {
        V3Support.V3Principal principal = userProof(proof);
        return ApiEnvelope.ok(personalService.internalProjection("FOOD_DRAFT", principal.accountId(), id));
    }

    @GetMapping("/internal/agent/stay-drafts/{id}")
    ApiEnvelope<Map<String, Object>> stayDraft(@PathVariable String id,
                                               @RequestHeader("X-Wudong-User-Proof") String proof) {
        V3Support.V3Principal principal = userProof(proof);
        return ApiEnvelope.ok(personalService.internalProjection("STAY_DRAFT", principal.accountId(), id));
    }

    @GetMapping("/internal/agent/orders/status")
    ApiEnvelope<Map<String, Object>> orderStatus(@RequestParam String threadId,
                                                 @RequestHeader("X-Wudong-User-Proof") String proof) {
        return ApiEnvelope.ok(orderService.statusForThread(userProof(proof).accountId(), threadId));
    }

    @PostMapping("/internal/agent/anonymous/interactions")
    ApiEnvelope<Map<String, Object>> anonymousInteraction(
            @RequestHeader("X-Wudong-Anonymous-Proof") String proof,
            @RequestBody Map<String, Object> body) {
        V3Support.onlyKeys(body, "threadId", "runId");
        return ApiEnvelope.ok(identityService.recordAnonymousInteraction(proof,
                V3Support.uuid(body, "threadId"), V3Support.uuid(body, "runId")));
    }

    private V3Support.V3Principal userProof(String proof) {
        if (proof == null || !proof.startsWith("Bearer ") || proof.length() <= 7) {
            throw new ApiRequestException(HttpStatus.FORBIDDEN, "FORBIDDEN", "用户证明无效");
        }
        V3Support.V3Principal principal = identityService.authenticate(proof.substring(7));
        if (!"USER".equals(principal.purpose())) {
            throw new ApiRequestException(HttpStatus.FORBIDDEN, "FORBIDDEN", "用户证明无效");
        }
        return principal;
    }

    private static List<Map<String, Object>> search(List<Map<String, Object>> values, String keywords,
                                                    Integer requestedLimit) {
        if (keywords == null || keywords.trim().isEmpty()
                || keywords.trim().codePointCount(0, keywords.trim().length()) > 200) {
            V3Support.bad("keywords 长度不符合要求");
        }
        int limit = requestedLimit == null ? 10 : requestedLimit;
        if (limit < 1 || limit > 10) {
            V3Support.bad("limit 必须在 1 到 10 之间");
        }
        String normalized = keywords.trim().toLowerCase(java.util.Locale.ROOT);
        return values.stream().filter(value -> V3Support.canonicalJson(value)
                        .toLowerCase(java.util.Locale.ROOT).contains(normalized)).limit(limit).toList();
    }
}
