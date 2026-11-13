package com.college.events.service;

import com.college.events.dto.MediaStorageDtos;
import com.college.events.model.MediaAsset;
import com.college.events.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only bounded inventory; it never deletes or exposes object keys. */
@Service
@RequiredArgsConstructor
public class MediaStorageInventoryService {
    private static final int MAX_FILES = 100_000;
    private final MediaAssetRepository assets;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    public MediaStorageDtos.Inventory measure() {
        Instant now = Instant.now();
        List<MediaAsset> tracked = assets.findAll();
        long active = tracked.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
        long expired = tracked.stream().filter(a -> a.getExpiresAt() != null && !a.getExpiresAt().isAfter(now)
                && !"PURGED".equals(a.getStatus())).count();
        long held = tracked.stream().filter(MediaAsset::isLegalHold).count();
        long failed = tracked.stream().filter(a -> "PURGE_FAILED".equals(a.getStatus())).count();
        long bytes = tracked.stream().mapToLong(MediaAsset::getSizeBytes).sum();
        Set<String> keys = new HashSet<>();
        tracked.forEach(a -> keys.add(normalize(a.getObjectKey())));
        long[] orphan = {0, 0};
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (Files.exists(root)) {
            try (var paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile).limit(MAX_FILES).forEach(path -> {
                    String relative = normalize(root.relativize(path).toString());
                    if (!keys.contains(relative)) {
                        orphan[0]++;
                        try { orphan[1] += Files.size(path); } catch (IOException ignored) { }
                    }
                });
            } catch (IOException ignored) { }
        }
        return new MediaStorageDtos.Inventory(tracked.size(), bytes, active, expired, held, failed,
                orphan[0], orphan[1], now);
    }

    private String normalize(String value) { return value == null ? "" : value.replace('\\', '/').replaceFirst("^/", ""); }
}
