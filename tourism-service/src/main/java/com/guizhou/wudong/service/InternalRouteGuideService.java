package com.guizhou.wudong.service;

import com.guizhou.wudong.api.CommunityViews;
import com.guizhou.wudong.domain.RouteNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class InternalRouteGuideService {
    private final CommunityService communityService;

    public InternalRouteGuideService(CommunityService communityService) {
        this.communityService = communityService;
    }

    public List<CommunityViews.PostView> search(String rawKeywords, List<String> rawTags) {
        String keywords = keywords(rawKeywords);
        List<String> tags = tags(rawTags);
        return communityService.publicPosts("ROUTE_GUIDE", null).stream()
                .filter(post -> tags.isEmpty() || post.tags().containsAll(tags))
                .filter(post -> keywords == null || contains(post, keywords))
                .toList();
    }

    private static boolean contains(CommunityViews.PostView post, String keywords) {
        String needle = keywords.toLowerCase(Locale.ROOT);
        if (textContains(post.title(), needle)
                || textContains(post.content(), needle)
                || textContains(post.routeSummary(), needle)
                || post.tags().stream().anyMatch(tag -> textContains(tag, needle))) {
            return true;
        }
        List<RouteNode> routeNodes = post.routeNodes();
        return routeNodes != null && routeNodes.stream()
                .anyMatch(node -> textContains(node.placeName(), needle) || textContains(node.note(), needle));
    }

    private static boolean textContains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String keywords(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 120) {
            throw new IllegalArgumentException("检索关键词无效");
        }
        return value;
    }

    private static List<String> tags(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 80
                    || value.contains(",") || !result.add(value)) {
                throw new IllegalArgumentException("标签筛选无效或重复");
            }
        }
        return List.copyOf(result);
    }
}
