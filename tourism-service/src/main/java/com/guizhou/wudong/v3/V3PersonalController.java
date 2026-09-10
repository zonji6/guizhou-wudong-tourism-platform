package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiEnvelope;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class V3PersonalController {
    private final V3PersonalService personalService;

    public V3PersonalController(V3PersonalService personalService) {
        this.personalService = personalService;
    }

    @GetMapping("/api/me/itineraries")
    ApiEnvelope<List<Map<String, Object>>> itineraries() {
        return ApiEnvelope.ok(personalService.itineraries(user().accountId()));
    }

    @GetMapping("/api/me/itineraries/{id}")
    ApiEnvelope<Map<String, Object>> itinerary(@PathVariable String id) {
        return ApiEnvelope.ok(personalService.itinerary(user().accountId(), id));
    }

    @PostMapping("/api/me/itineraries")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> createItinerary(
            @RequestHeader("Idempotency-Key") String requestKey, @RequestBody Map<String, Object> body) {
        V3PersonalService.SaveResult result = personalService.createItinerary(user().accountId(), requestKey, body);
        return created(result);
    }

    @PutMapping("/api/me/itineraries/{id}")
    ApiEnvelope<Map<String, Object>> saveItinerary(@PathVariable String id,
                                                   @RequestHeader("Idempotency-Key") String requestKey,
                                                   @RequestBody Map<String, Object> body) {
        return ApiEnvelope.ok(personalService.saveItinerary(user().accountId(), id, requestKey, body).receipt());
    }

    @PostMapping("/api/me/itineraries/adoptions")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> adoptItineraryCreate(
            @RequestHeader("Idempotency-Key") String requestKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String userProof,
            @RequestHeader(value = "X-Wudong-Anonymous-Proof", required = false) String anonymousProof,
            @RequestBody Map<String, Object> body) {
        return created(personalService.adopt("ITINERARY", true, user().accountId(), null, requestKey,
                body, userProof, anonymousProof));
    }

    @PostMapping("/api/me/itineraries/{id}/adoptions")
    ApiEnvelope<Map<String, Object>> adoptItineraryUpdate(
            @PathVariable String id, @RequestHeader("Idempotency-Key") String requestKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String userProof,
            @RequestHeader(value = "X-Wudong-Anonymous-Proof", required = false) String anonymousProof,
            @RequestBody Map<String, Object> body) {
        return ApiEnvelope.ok(personalService.adopt("ITINERARY", false, user().accountId(), id, requestKey,
                body, userProof, anonymousProof).receipt());
    }

    @GetMapping("/api/me/{kind:food-drafts|stay-drafts}")
    ApiEnvelope<List<Map<String, Object>>> drafts(@PathVariable String kind) {
        return ApiEnvelope.ok(personalService.drafts(type(kind), user().accountId()));
    }

    @GetMapping("/api/me/{kind:food-drafts|stay-drafts}/{id}")
    ApiEnvelope<Map<String, Object>> draft(@PathVariable String kind, @PathVariable String id) {
        return ApiEnvelope.ok(personalService.draft(type(kind), user().accountId(), id));
    }

    @PutMapping("/api/me/{kind:food-drafts|stay-drafts}/{id}")
    ApiEnvelope<Map<String, Object>> saveDraft(@PathVariable String kind, @PathVariable String id,
                                               @RequestHeader("Idempotency-Key") String requestKey,
                                               @RequestBody Map<String, Object> body) {
        return ApiEnvelope.ok(personalService.saveDraft(type(kind), user().accountId(), id, requestKey, body).receipt());
    }

    @PostMapping("/api/me/{kind:food-drafts|stay-drafts}/adoptions")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> adoptDraftCreate(
            @PathVariable String kind, @RequestHeader("Idempotency-Key") String requestKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String userProof,
            @RequestHeader(value = "X-Wudong-Anonymous-Proof", required = false) String anonymousProof,
            @RequestBody Map<String, Object> body) {
        return created(personalService.adopt(resourceType(kind), true, user().accountId(), null, requestKey,
                body, userProof, anonymousProof));
    }

    @PostMapping("/api/me/{kind:food-drafts|stay-drafts}/{id}/adoptions")
    ApiEnvelope<Map<String, Object>> adoptDraftUpdate(
            @PathVariable String kind, @PathVariable String id,
            @RequestHeader("Idempotency-Key") String requestKey,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String userProof,
            @RequestHeader(value = "X-Wudong-Anonymous-Proof", required = false) String anonymousProof,
            @RequestBody Map<String, Object> body) {
        return ApiEnvelope.ok(personalService.adopt(resourceType(kind), false, user().accountId(), id, requestKey,
                body, userProof, anonymousProof).receipt());
    }

    private static V3Support.V3Principal user() {
        return V3Support.principal("USER");
    }

    private static String type(String kind) {
        return "food-drafts".equals(kind) ? "FOOD" : "STAY";
    }

    private static String resourceType(String kind) {
        return type(kind) + "_DRAFT";
    }

    private static ResponseEntity<ApiEnvelope<Map<String, Object>>> created(V3PersonalService.SaveResult result) {
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiEnvelope.ok(result.receipt()));
    }
}
