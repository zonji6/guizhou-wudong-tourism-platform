package com.guizhou.wudong.api;

import jakarta.validation.constraints.NotBlank;

public record KnowledgeDocumentRequest(@NotBlank String title, @NotBlank String content, String tags) {}
