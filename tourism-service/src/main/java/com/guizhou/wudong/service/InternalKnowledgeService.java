package com.guizhou.wudong.service;

import com.guizhou.wudong.api.InternalKnowledgeViews;
import com.guizhou.wudong.domain.KnowledgeDocument;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class InternalKnowledgeService {
    private final TourismService tourismService;

    public InternalKnowledgeService(TourismService tourismService) {
        this.tourismService = tourismService;
    }

    public List<InternalKnowledgeViews.KnowledgeView> search(String rawKeywords) {
        String keywords = keywords(rawKeywords);
        return tourismService.knowledge(null).stream()
                .map(InternalKnowledgeService::view)
                .filter(document -> keywords == null || contains(document, keywords))
                .toList();
    }

    private static InternalKnowledgeViews.KnowledgeView view(KnowledgeDocument document) {
        return new InternalKnowledgeViews.KnowledgeView(
                document.id(),
                document.title(),
                document.content(),
                tags(document.tags()),
                document.demoData());
    }

    private static boolean contains(InternalKnowledgeViews.KnowledgeView document, String keywords) {
        String needle = keywords.toLowerCase(Locale.ROOT);
        return textContains(document.title(), needle)
                || textContains(document.content(), needle)
                || document.tags().stream().anyMatch(tag -> textContains(tag, needle));
    }

    private static boolean textContains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String keywords(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || !value.equals(value.strip()) || value.length() > 120) {
            throw new IllegalArgumentException("检索关键词无效");
        }
        return value;
    }

    private static List<String> tags(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String value : csv.split(",")) {
            String stripped = value.strip();
            if (!stripped.isBlank()) {
                values.add(stripped);
            }
        }
        return List.copyOf(values);
    }
}
