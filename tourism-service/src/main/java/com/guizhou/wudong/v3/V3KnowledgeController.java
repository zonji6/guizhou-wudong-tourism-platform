package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class V3KnowledgeController {
    private final V3KnowledgeService knowledgeService;

    public V3KnowledgeController(V3KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/api/admin/knowledge-sources")
    ApiEnvelope<List<Map<String, Object>>> sources() {
        admin();
        return ApiEnvelope.ok(knowledgeService.sources());
    }

    @GetMapping("/api/admin/knowledge-sources/{id}")
    ApiEnvelope<Map<String, Object>> source(@PathVariable String id) {
        admin();
        return ApiEnvelope.ok(knowledgeService.source(id));
    }

    @PostMapping("/api/admin/knowledge-sources")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> createSource(@RequestBody Map<String, Object> body) {
        admin();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(knowledgeService.createSource(body)));
    }

    @PutMapping("/api/admin/knowledge-sources/{id}")
    ApiEnvelope<Map<String, Object>> updateSource(@PathVariable String id, @RequestBody Map<String, Object> body) {
        admin();
        return ApiEnvelope.ok(knowledgeService.updateSource(id, body));
    }

    @GetMapping("/api/admin/knowledge-documents")
    ApiEnvelope<List<Map<String, Object>>> adminDocuments() {
        admin();
        return ApiEnvelope.ok(knowledgeService.adminDocuments());
    }

    @GetMapping("/api/admin/knowledge-documents/{id}")
    ApiEnvelope<Map<String, Object>> adminDocument(@PathVariable String id) {
        admin();
        return ApiEnvelope.ok(knowledgeService.adminDocument(id));
    }

    @PostMapping("/api/admin/knowledge-documents")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> createDocument(@RequestBody Map<String, Object> body) {
        admin();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(knowledgeService.createDocument(body)));
    }

    @PutMapping("/api/admin/knowledge-documents/{id}/draft")
    ApiEnvelope<Map<String, Object>> updateDocument(@PathVariable String id, @RequestBody Map<String, Object> body) {
        admin();
        return ApiEnvelope.ok(knowledgeService.updateDocument(id, body));
    }

    @PostMapping("/api/admin/knowledge-documents/{id}/publications")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> publish(
            @PathVariable String id, @RequestHeader("Idempotency-Key") String requestKey,
            @RequestBody Map<String, Object> body) {
        V3KnowledgeService.PublishResult result = knowledgeService.publish(admin().accountId(), id, requestKey, body);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(ApiEnvelope.ok(result.data()));
    }

    @PostMapping("/api/admin/knowledge-documents/{id}/unpublish")
    ApiEnvelope<Map<String, Object>> unpublish(@PathVariable String id, @RequestBody Map<String, Object> body) {
        admin();
        return ApiEnvelope.ok(knowledgeService.unpublish(id, body));
    }

    @GetMapping("/api/admin/knowledge-publish-tasks")
    ApiEnvelope<List<Map<String, Object>>> tasks(@RequestParam(required = false) String documentId) {
        admin();
        return ApiEnvelope.ok(knowledgeService.tasks(documentId));
    }

    @GetMapping("/api/admin/knowledge-publish-tasks/{taskId}")
    ApiEnvelope<Map<String, Object>> task(@PathVariable String taskId) {
        admin();
        return ApiEnvelope.ok(knowledgeService.task(taskId));
    }

    @GetMapping("/api/knowledge-documents")
    ApiEnvelope<List<Map<String, Object>>> publicDocuments(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "tag", required = false) List<String> tags) {
        return ApiEnvelope.ok(knowledgeService.publicDocuments(keyword, tags == null ? List.of() : tags));
    }

    @GetMapping("/api/knowledge-documents/{id}")
    ApiEnvelope<Map<String, Object>> publicDocument(@PathVariable String id) {
        return ApiEnvelope.ok(knowledgeService.publicDocument(id));
    }

    private static V3Support.V3Principal admin() {
        return V3Support.principal("ADMIN");
    }
}
