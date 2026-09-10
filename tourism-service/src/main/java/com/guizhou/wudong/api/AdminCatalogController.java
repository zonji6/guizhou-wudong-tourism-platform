package com.guizhou.wudong.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.guizhou.wudong.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("legacy-v2")
@RequestMapping("/api/admin")
public class AdminCatalogController {
    private final CatalogService catalogService;

    public AdminCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/{resource:merchants|products|foods|stays|room-types|places}")
    ApiEnvelope<?> list(@PathVariable String resource) {
        return ApiEnvelope.ok(catalogService.adminList(CatalogRequests.Kind.fromPath(resource)));
    }

    @GetMapping("/{resource:merchants|products|foods|stays|room-types|places}/{id}")
    ApiEnvelope<?> detail(@PathVariable String resource, @PathVariable String id) {
        return ApiEnvelope.ok(catalogService.adminDetail(CatalogRequests.Kind.fromPath(resource), id));
    }

    @PostMapping("/{resource:merchants|products|foods|stays|room-types|places}")
    ApiEnvelope<?> create(@PathVariable String resource, @RequestBody JsonNode body) {
        return ApiEnvelope.ok(catalogService.create(CatalogRequests.Kind.fromPath(resource), body));
    }

    @PatchMapping("/{resource:merchants|products|foods|stays|room-types|places}/{id}")
    ApiEnvelope<?> patch(@PathVariable String resource, @PathVariable String id, @RequestBody JsonNode body) {
        return ApiEnvelope.ok(catalogService.patch(CatalogRequests.Kind.fromPath(resource), id, body));
    }

    @PatchMapping("/{resource:merchants|products|foods|stays|room-types|places}/{id}/catalog-status")
    ApiEnvelope<?> catalogStatus(@PathVariable String resource, @PathVariable String id, @RequestBody JsonNode body) {
        return ApiEnvelope.ok(catalogService.catalogStatus(CatalogRequests.Kind.fromPath(resource), id, body));
    }
}
