package com.guizhou.wudong.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record StayBooking(
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
) {}
