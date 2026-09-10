package com.guizhou.wudong.service;

import com.guizhou.wudong.api.ApiRequestException;
import com.guizhou.wudong.api.CommunityRequests;
import com.guizhou.wudong.api.CommunityViews;
import com.guizhou.wudong.domain.PostType;
import com.guizhou.wudong.domain.RouteNode;
import com.guizhou.wudong.repository.CommunityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CommunityService {
    private static final String PUBLISHED = "PUBLISHED";
    private static final String GENERATED_AUTHOR_NAME = "乌东旅人";

    private final CommunityRepository repository;

    public CommunityService(CommunityRepository repository) {
        this.repository = repository;
    }

    public List<CommunityViews.PostView> publicPosts(String rawType, String rawTag) {
        PostType type = postTypeFilter(rawType);
        String tag = tagFilter(rawTag);
        return repository.findPublished(type, tag).stream().map(CommunityService::publicView).toList();
    }

    @Transactional
    public CommunityViews.PostView create(String visitorId, CommunityRequests.PostCreate request) {
        validatePlaces(request.routeNodes());
        String id = UUID.randomUUID().toString();
        repository.insert(id, visitorId, request.title(), request.content(), GENERATED_AUTHOR_NAME,
                request.tags().isEmpty() ? null : String.join(",", request.tags()), request.postType(),
                request.routeSummary(), request.routeNodes());
        return publicView(repository.findPublishedById(id).orElseThrow(CommunityService::resourceNotFound));
    }

    private void validatePlaces(List<RouteNode> routeNodes) {
        if (routeNodes == null) {
            return;
        }
        Set<String> checked = new LinkedHashSet<>();
        for (RouteNode node : routeNodes) {
            if (node.placeId() == null || !checked.add(node.placeId())) {
                continue;
            }
            String status = repository.findPlaceStatus(node.placeId())
                    .orElseThrow(CommunityService::resourceNotFound);
            if (!PUBLISHED.equals(status)) {
                throw new ApiRequestException(HttpStatus.CONFLICT, "CATALOG_NOT_PUBLISHED", "路线节点地点当前未发布");
            }
        }
    }

    private static CommunityViews.PostView publicView(CommunityRepository.StoredPost post) {
        boolean routeGuide = post.postType() == PostType.ROUTE_GUIDE;
        return new CommunityViews.PostView(
                post.id(),
                routeGuide ? post.title() : null,
                post.content(),
                post.authorName(),
                tags(post.tags()),
                post.postType(),
                routeGuide ? post.routeSummary() : null,
                routeGuide ? sortedRouteNodes(post.routeNodes()) : null,
                post.demoData(),
                post.publishedAt());
    }

    private static List<RouteNode> sortedRouteNodes(List<RouteNode> routeNodes) {
        if (routeNodes == null) {
            return null;
        }
        return routeNodes.stream().sorted(Comparator.comparingInt(RouteNode::sequence)).toList();
    }

    private static PostType postTypeFilter(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("动态类型筛选无效");
        }
        try {
            return PostType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("动态类型筛选无效");
        }
    }

    private static String tagFilter(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 80 || value.contains(",")) {
            throw new IllegalArgumentException("标签筛选无效");
        }
        return value;
    }

    private static List<String> tags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : csv.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return List.copyOf(values);
    }

    private static ApiRequestException resourceNotFound() {
        return new ApiRequestException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "未找到或无权访问该资源");
    }
}
