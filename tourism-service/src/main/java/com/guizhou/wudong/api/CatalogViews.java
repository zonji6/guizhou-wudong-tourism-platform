package com.guizhou.wudong.api;

import com.guizhou.wudong.domain.CatalogStatus;
import com.guizhou.wudong.domain.MapStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CatalogViews {
    private CatalogViews() {}

    public record MerchantView(String id, String name, String description, String contactPhone,
                               boolean demoData, CatalogStatus catalogStatus, LocalDateTime createdAt) {}

    public record ProductView(String id, String merchantId, String merchantName, String name, String description,
                              BigDecimal price, String pickupPoint, List<String> tags, String imageUrl,
                              boolean demoData, CatalogStatus catalogStatus) {}

    public record FoodView(String id, String merchantId, String merchantName, String name, String description,
                           BigDecimal price, String visitTimeText, List<String> tags, String imageUrl,
                           boolean demoData, CatalogStatus catalogStatus) {}

    public record RoomTypeView(String id, String stayPropertyId, String stayPropertyName, String name,
                               String description, int maxGuests, BigDecimal price, String imageUrl,
                               boolean demoData, CatalogStatus catalogStatus) {}

    public record StayView(String id, String merchantId, String merchantName, String name, String description,
                           String locationText, List<String> tags, String imageUrl, boolean demoData,
                           CatalogStatus catalogStatus, List<RoomTypeView> roomTypes) {}

    public record PlaceView(String id, String name, String category, String description, BigDecimal latitude,
                            BigDecimal longitude, List<String> tags, String imageUrl, boolean demoData,
                            CatalogStatus catalogStatus) {}

    public record MapPlacesView(MapStatus mapStatus, List<PlaceView> places, String message) {}
}
