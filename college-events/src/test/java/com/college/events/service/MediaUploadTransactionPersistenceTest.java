package com.college.events.service;

import com.college.events.repository.MediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real repository transactions: a later enclosing operation can fail after save succeeds. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "logging.file.name=target/test-logs/media-upload-transaction.log"}, showSql = false)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MediaUploadTransactionPersistenceTest {
    @TempDir Path root;
    @Autowired MediaAssetRepository assets;
    @Autowired PlatformTransactionManager transactions;
    FileStorageService storage;
    TransactionTemplate tx;

    @BeforeEach void setup() {
        assets.deleteAll();
        storage = new FileStorageService(assets);
        ReflectionTestUtils.setField(storage, "uploadDir", root.toString());
        tx = new TransactionTemplate(transactions);
    }

    @AfterEach void completionLeavesNoIdentityAnchors() throws Exception {
        Path anchors = root.resolve(".pending-upload-identities");
        if (Files.exists(anchors)) {
            try (var entries = Files.list(anchors)) { assertThat(entries.count()).isZero(); }
        }
    }

    @Test void enclosingFailureRollsBackMetadataAndDeletesOnlyItsNewBytes() throws Exception {
        Path unrelated = Files.createDirectories(root.resolve("events")).resolve("untracked.png");
        Files.writeString(unrelated, "synthetic unrelated bytes");
        AtomicReference<String> key = new AtomicReference<>();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            key.set(storage.storeEventBanner(image()));
            assertThat(assets.findByObjectKey(key.get())).isPresent();
            assertThat(root.resolve(key.get())).exists();
            throw new IllegalStateException("synthetic later event failure");
        })).isInstanceOf(IllegalStateException.class).hasMessage("synthetic later event failure");

        assertThat(assets.findByObjectKey(key.get())).isEmpty();
        assertThat(root.resolve(key.get())).doesNotExist();
        assertThat(Files.readString(unrelated)).isEqualTo("synthetic unrelated bytes");
    }

    @Test void rollbackOnlyCompletionAlsoRemovesNewBytes() {
        String key = tx.execute(status -> {
            String uploaded = storage.storeProfilePicture(image());
            status.setRollbackOnly();
            return uploaded;
        });
        assertThat(assets.findByObjectKey(key)).isEmpty();
        assertThat(root.resolve(key)).doesNotExist();
    }

    @Test void failureDuringCommitPreparationRemovesRolledBackUpload() {
        AtomicReference<String> key = new AtomicReference<>();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            key.set(storage.storeEventBanner(image()));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("synthetic commit preparation failure");
                }
            });
        })).isInstanceOf(IllegalStateException.class).hasMessage("synthetic commit preparation failure");
        assertThat(assets.findByObjectKey(key.get())).isEmpty();
        assertThat(root.resolve(key.get())).doesNotExist();
    }

    @Test void committedUploadKeepsBytesAndThirtyDayMetadata() {
        String key = tx.execute(status -> storage.storeEventBanner(image()));
        var asset = assets.findByObjectKey(key).orElseThrow();
        assertThat(root.resolve(key)).exists();
        assertThat(asset.getExpiresAt()).isEqualTo(asset.getUploadAcceptedAt().plusSeconds(30L * 24 * 3600));
    }

    @Test void repositoryOwnedCommitWithoutEnclosingTransactionKeepsUpload() {
        String key = storage.storeProfilePicture(image());
        assertThat(assets.findByObjectKey(key)).isPresent();
        assertThat(root.resolve(key)).exists();
    }

    @Test void rollbackPreservesChangedObjectForReconciliation() {
        AtomicReference<String> key = new AtomicReference<>();
        tx.executeWithoutResult(status -> {
            key.set(storage.storeEventBanner(image()));
            try {
                // Same file identity, different contents: digest guard must prevent deletion.
                Files.writeString(root.resolve(key.get()), "synthetic replacement evidence");
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            status.setRollbackOnly();
        });
        assertThat(assets.findByObjectKey(key.get())).isEmpty();
        assertThat(root.resolve(key.get())).hasContent("synthetic replacement evidence");
    }

    @Test void rollbackPreservesDifferentFileEvenWhenItsBytesMatch() {
        AtomicReference<String> key = new AtomicReference<>();
        tx.executeWithoutResult(status -> {
            key.set(storage.storeEventBanner(image()));
            try {
                Path original = root.resolve(key.get());
                Path replacement = original.resolveSibling("replacement-evidence.png");
                Files.copy(original, replacement);
                Files.delete(original);
                Files.move(replacement, original);
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            status.setRollbackOnly();
        });
        assertThat(assets.findByObjectKey(key.get())).isEmpty();
        assertThat(root.resolve(key.get())).exists();
    }

    private MockMultipartFile image() {
        try {
            var bytes = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
            return new MockMultipartFile("file", "synthetic.png", "image/png", bytes.toByteArray());
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
}
