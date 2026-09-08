package com.guizhou.wudong.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Booking(
        String id,
        String serviceId,
        String serviceName,
        LocalDate travelDate,
        int peopleCount,
        String contactName,
        String contactPhone,
        String note,
        String status,
        String source,
        String threadId,
        LocalDateTime createdAt
) {}
