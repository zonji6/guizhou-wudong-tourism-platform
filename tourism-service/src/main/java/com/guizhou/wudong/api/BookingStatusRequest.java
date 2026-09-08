package com.guizhou.wudong.api;

import jakarta.validation.constraints.NotBlank;

public record BookingStatusRequest(@NotBlank String status) {}
