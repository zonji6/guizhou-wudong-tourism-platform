package com.guizhou.wudong.domain;

import java.time.LocalDateTime;
import java.util.List;

public record AgentRunSummary(
        String id,
        String threadId,
        String nodeName,
        String toolCategory,
        List<String> sourceTitles,
        long durationMs,
        String finalStatus,
        LocalDateTime createdAt
) {}
