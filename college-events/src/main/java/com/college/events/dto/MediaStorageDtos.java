package com.college.events.dto;

import java.time.Instant;

public final class MediaStorageDtos {
    private MediaStorageDtos() { }

    public record Inventory(long trackedObjects, long trackedBytes, long activeObjects,
                            long expiredObjects, long heldObjects, long purgeFailedObjects,
                            long orphanObjects, long orphanBytes, Instant measuredAt) { }
}
