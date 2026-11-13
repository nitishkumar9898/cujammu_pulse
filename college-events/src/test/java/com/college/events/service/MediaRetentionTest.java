package com.college.events.service;

import com.college.events.model.MediaAsset;
import com.college.events.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaRetentionTest {
    @TempDir Path tempDir;

    @Test
    void acceptedUploadGetsImmutableThirtyDayDeadlineAndDigest() throws Exception {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        FileStorageService storage = new FileStorageService(repository);
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        Instant before = Instant.now();

        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        String key = storage.storeImage(new MockMultipartFile("file", "banner.png", "image/png", output.toByteArray()), "events");

        ArgumentCaptor<MediaAsset> captor = ArgumentCaptor.forClass(MediaAsset.class);
        verify(repository).save(captor.capture());
        MediaAsset asset = captor.getValue();
        assertThat(key).isEqualTo(asset.getObjectKey());
        assertThat(asset.getExpiresAt()).isBetween(before.plus(Duration.ofDays(30)), Instant.now().plus(Duration.ofDays(30).plusSeconds(1)));
        assertThat(asset.getSha256()).hasSize(64);
    }

    @Test void rejectsSpoofedOrUndecodableImageWithoutCreatingMetadata() {
        var repository = mock(MediaAssetRepository.class);
        var storage = new FileStorageService(repository);
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> storage.storeImage(
                new MockMultipartFile("file", "malicious.png", "image/png", "<script>synthetic</script>".getBytes()), "events"));
        verify(repository, never()).save(any());
    }

    @Test
    void cleanupDoesNotExtendExpiryFromModificationTime() throws Exception {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        FileStorageService storage = new FileStorageService(repository);
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        Path file = Files.createDirectories(tempDir.resolve("events")).resolve("old.png");
        Files.write(file, new byte[]{4});
        MediaAsset expired = MediaAsset.builder().objectKey("events/old.png").expiresAt(Instant.now().minusSeconds(1))
                .uploadAcceptedAt(Instant.now().minus(Duration.ofDays(31))).mediaType("image/png").sizeBytes(1).build();
        when(repository.findByExpiresAtLessThanEqualAndStatusOrderByExpiresAtAsc(any(), eq("ACTIVE"))).thenReturn(List.of(expired));

        long deleted = storage.deleteMediaOlderThan(Duration.ofDays(365));

        assertThat(deleted).isEqualTo(1);
        assertThat(Files.exists(file)).isFalse();
        assertThat(expired.getStatus()).isEqualTo("PURGED");
        verify(repository).save(expired);
    }

    @Test
    void activeLegalHoldIsNeverDeletedByUntrackedFilesystemSweep() throws Exception {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        FileStorageService storage = new FileStorageService(repository);
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        Path file = Files.createDirectories(tempDir.resolve("events")).resolve("held.png");
        Files.write(file, new byte[]{9});
        MediaAsset held = MediaAsset.builder().objectKey("events/held.png").expiresAt(Instant.now().minusSeconds(1))
                .uploadAcceptedAt(Instant.now().minus(Duration.ofDays(31))).mediaType("image/png").sizeBytes(1)
                .legalHold(true).legalHoldUntil(Instant.now().plus(Duration.ofDays(2))).build();
        when(repository.findByExpiresAtLessThanEqualAndStatusOrderByExpiresAtAsc(any(), eq("ACTIVE"))).thenReturn(List.of(held));

        assertThat(storage.deleteMediaOlderThan(Duration.ofDays(365))).isZero();
        assertThat(Files.exists(file)).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void failedDeletionIsDurableAndEligibleForLaterRetry() throws Exception {
        MediaAssetRepository repository = mock(MediaAssetRepository.class);
        FileStorageService storage = new FileStorageService(repository);
        ReflectionTestUtils.setField(storage, "uploadDir", tempDir.toString());
        Path blocked = Files.createDirectories(tempDir.resolve("events")).resolve("blocked.png");
        Files.createDirectories(blocked.resolve("child"));
        MediaAsset asset = MediaAsset.builder().objectKey("events/blocked.png").expiresAt(Instant.now().minusSeconds(1))
                .uploadAcceptedAt(Instant.now().minus(Duration.ofDays(31))).mediaType("image/png").sizeBytes(1).build();
        when(repository.findByExpiresAtLessThanEqualAndStatusOrderByExpiresAtAsc(any(), eq("ACTIVE"))).thenReturn(List.of(asset));

        assertThat(storage.deleteMediaOlderThan(Duration.ofDays(30))).isZero();
        assertThat(asset.getStatus()).isEqualTo("PURGE_FAILED");
        assertThat(asset.getPurgeAttempts()).isEqualTo(1);
        verify(repository).save(asset);
    }
}
