package com.guizhou.wudong.api;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record BookingRequest(
        @NotBlank String serviceId,
        @NotNull @FutureOrPresent LocalDate travelDate,
        @Min(1) int peopleCount,
        @NotBlank String contactName,
        @NotBlank String contactPhone,
        String note
) {}
