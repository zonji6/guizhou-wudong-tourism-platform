package com.guizhou.wudong.v3;

import com.guizhou.wudong.api.ApiEnvelope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class V3CommunityController {
    private final V3CommunityService communityService;

    public V3CommunityController(V3CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/api/posts")
    ApiEnvelope<List<Map<String, Object>>> list(@RequestParam(required = false) String type,
                                                @RequestParam(required = false) String tag) {
        return ApiEnvelope.ok(communityService.list(type, tag));
    }

    @GetMapping("/api/posts/{id}")
    ApiEnvelope<Map<String, Object>> get(@PathVariable String id) {
        return ApiEnvelope.ok(communityService.get(id));
    }

    @PostMapping("/api/posts")
    ResponseEntity<ApiEnvelope<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> created = communityService.create(V3Support.principal("USER"), body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.ok(created));
    }
}
