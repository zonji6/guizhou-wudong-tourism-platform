package com.guizhou.wudong.api;

import com.guizhou.wudong.service.InternalKnowledgeService;
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
public class InternalKnowledgeController {
    private static final Set<String> ALLOWED_QUERY = Set.of("keywords");

    private final InternalKnowledgeService internalKnowledgeService;

    public InternalKnowledgeController(InternalKnowledgeService internalKnowledgeService) {
        this.internalKnowledgeService = internalKnowledgeService;
    }

    @GetMapping("/knowledge/search")
    ApiEnvelope<List<InternalKnowledgeViews.KnowledgeView>> search(
            @RequestParam(required = false) String keywords, HttpServletRequest request) {
        rejectUnknownOrRepeatedQuery(request);
        return ApiEnvelope.ok(internalKnowledgeService.search(keywords));
    }

    private static void rejectUnknownOrRepeatedQuery(HttpServletRequest request) {
        if (!ALLOWED_QUERY.containsAll(request.getParameterMap().keySet())) {
            throw new IllegalArgumentException("存在不支持的查询参数");
        }
        String[] keywords = request.getParameterValues("keywords");
        if (keywords != null && keywords.length != 1) {
            throw new IllegalArgumentException("查询参数只能提供一次");
        }
    }
}
