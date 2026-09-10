package com.guizhou.wudong.service;

import com.guizhou.wudong.api.CatalogViews;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class InternalCatalogService {
    private final CatalogService catalogService;

    public InternalCatalogService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    public List<CatalogViews.ProductView> products(String rawKeywords, List<String> rawTags) {
        String keywords = keywords(rawKeywords);
        return catalogService.publicProducts(null, tags(rawTags)).stream()
                .filter(item -> keywords == null || contains(keywords, item.name(), item.description(), item.tags()))
                .toList();
    }

    public List<CatalogViews.FoodView> foods(String rawKeywords, List<String> rawTags) {
        String keywords = keywords(rawKeywords);
        return catalogService.publicFoods(null, tags(rawTags)).stream()
                .filter(item -> keywords == null || contains(keywords, item.name(), item.description(), item.tags()))
                .toList();
    }

    public List<CatalogViews.StayView> stays(
            String rawKeywords,
            List<String> rawTags,
            Integer peopleCount,
            String roomTypeId) {
        String keywords = keywords(rawKeywords);
        return catalogService.publicStays(peopleCount, roomTypeId, tags(rawTags)).stream()
                .filter(item -> keywords == null || stayContains(keywords, item))
                .toList();
    }

    public CatalogViews.MapPlacesView places(String rawKeywords, List<String> rawTags) {
        keywords(rawKeywords);
        return catalogService.publicPlaces(null, tags(rawTags));
    }

    private static boolean stayContains(String keywords, CatalogViews.StayView stay) {
        if (contains(keywords, stay.name(), stay.description(), stay.tags())) {
            return true;
        }
        return stay.roomTypes().stream()
                .anyMatch(room -> contains(keywords, room.name(), room.description(), List.of()));
    }

    private static boolean contains(String keywords, String name, String description, List<String> tags) {
        String needle = keywords.toLowerCase(Locale.ROOT);
        if (name.toLowerCase(Locale.ROOT).contains(needle)
                || description.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return tags.stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(needle));
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
            return null;
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
