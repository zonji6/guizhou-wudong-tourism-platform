package com.guizhou.wudong.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.guizhou.wudong.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Set;

@RestController
@Profile("legacy-v2")
@RequestMapping("/api")
public class PublicCommunityController {
    private final CommunityService communityService;

    public PublicCommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/posts")
    ApiEnvelope<List<CommunityViews.PostView>> posts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String tag,
            HttpServletRequest request) {
        rejectUnknownOrRepeatedQuery(request, Set.of("type", "tag"));
        return ApiEnvelope.ok(communityService.publicPosts(type, tag));
    }

    @PostMapping("/posts")
    ApiEnvelope<CommunityViews.PostView> create(@RequestBody JsonNode body, HttpServletRequest request) {
        rejectUnknownOrRepeatedQuery(request, Set.of());
        String visitorId = VisitorIdentity.require(request);
        return ApiEnvelope.ok(communityService.create(visitorId, CommunityRequests.post(body)));
    }

    private static void rejectUnknownOrRepeatedQuery(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet())) {
            throw new IllegalArgumentException("存在不支持的查询参数");
        }
        for (String parameter : allowed) {
            String[] values = request.getParameterValues(parameter);
            if (values != null && values.length != 1) {
                throw new IllegalArgumentException("查询参数只能提供一次");
            }
        }
    }
}
