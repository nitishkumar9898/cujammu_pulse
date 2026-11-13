package com.college.events.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class FeatureControlAuditDtos {
    private FeatureControlAuditDtos() { }
    public record PageView(List<Item> items, int page, boolean hasNext) { }
    public record Item(long id, String role, String featureKey, boolean enabled, String reason,
                       LocalDateTime timestamp) { }
}
