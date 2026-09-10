package com.guizhou.wudong.api;

import com.guizhou.wudong.domain.FoodOrder;
import com.guizhou.wudong.domain.FoodOrderStatus;
import com.guizhou.wudong.domain.ProductOrder;
import com.guizhou.wudong.domain.ProductOrderStatus;
import com.guizhou.wudong.domain.StayBooking;
import com.guizhou.wudong.domain.StayBookingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class OrderViews {
    private OrderViews() {
    }

    public record ProductPublicView(
            String id,
            String productId,
            String productName,
            int quantity,
            String pickupPoint,
            String note,
            ProductOrderStatus status,
            boolean demoData,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ProductPublicView from(ProductOrder order) {
            return new ProductPublicView(order.id(), order.productId(), order.productName(), order.quantity(),
                    order.pickupPoint(), order.note(), order.status(), order.demoData(), order.createdAt(),
                    order.updatedAt());
        }
    }

    public record FoodPublicView(
            String id,
            String foodItemId,
            String foodItemName,
            LocalDateTime visitAt,
            int peopleCount,
            String note,
            FoodOrderStatus status,
            boolean demoData,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static FoodPublicView from(FoodOrder order) {
            return new FoodPublicView(order.id(), order.foodItemId(), order.foodItemName(), order.visitAt(),
                    order.peopleCount(), order.note(), order.status(), order.demoData(), order.createdAt(),
                    order.updatedAt());
        }
    }

    public record StayPublicView(
            String id,
            String roomTypeId,
            String roomTypeName,
            LocalDate checkInDate,
            int peopleCount,
            String note,
            StayBookingStatus status,
            boolean demoData,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static StayPublicView from(StayBooking booking) {
            return new StayPublicView(booking.id(), booking.roomTypeId(), booking.roomTypeName(),
                    booking.checkInDate(), booking.peopleCount(), booking.note(), booking.status(),
                    booking.demoData(), booking.createdAt(), booking.updatedAt());
        }
    }

    public record ProductAdminView(
            String id,
            String visitorId,
            String productId,
            String productName,
            int quantity,
            String pickupPoint,
            String contactName,
            String contactPhone,
            String note,
            ProductOrderStatus status,
            String sourceThreadId,
            boolean demoData,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ProductAdminView from(ProductOrder order) {
            return new ProductAdminView(order.id(), order.visitorId(), order.productId(), order.productName(),
                    order.quantity(), order.pickupPoint(), order.contactName(), order.contactPhone(), order.note(),
                    order.status(), order.sourceThreadId(), order.demoData(), order.createdAt(), order.updatedAt());
        }
    }

    public record FoodAdminView(
            String id,
            String visitorId,
            String foodItemId,
            String foodItemName,
            LocalDateTime visitAt,
            int peopleCount,
            String contactName,
            String contactPhone,
            String note,
            FoodOrderStatus status,
            String sourceThreadId,
            boolean demoData,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static FoodAdminView from(FoodOrder order) {
            return new FoodAdminView(order.id(), order.visitorId(), order.foodItemId(), order.foodItemName(),
                    order.visitAt(), order.peopleCount(), order.contactName(), order.contactPhone(), order.note(),
                    order.status(), order.sourceThreadId(), order.demoData(), order.createdAt(), order.updatedAt());
        }
    }

    public record StayAdminView(
            String id,
            String visitorId,
            String roomTypeId,
            String roomTypeName,
            LocalDate checkInDate,
            int peopleCount,
            String contactName,
            String contactPhone,
            String note,
            StayBookingStatus status,
            String sourceThreadId,
            boolean demoData,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static StayAdminView from(StayBooking booking) {
            return new StayAdminView(booking.id(), booking.visitorId(), booking.roomTypeId(), booking.roomTypeName(),
                    booking.checkInDate(), booking.peopleCount(), booking.contactName(), booking.contactPhone(),
                    booking.note(), booking.status(), booking.sourceThreadId(), booking.demoData(), booking.createdAt(),
                    booking.updatedAt());
        }
    }
}
