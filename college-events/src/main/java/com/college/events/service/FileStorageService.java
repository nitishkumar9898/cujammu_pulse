package com.college.events.service;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.college.events.model.MediaAsset;
import com.college.events.repository.MediaAssetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.security.MessageDigest;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

    private static final String EVENTS_DIR = "events";
    private static final String PROFILES_DIR = "profiles";
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/webp"
    );
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Duration MAX_RETENTION = Duration.ofDays(30);

    private final MediaAssetRepository mediaAssetRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.college.events.security.DomainAccess access;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    public String storeEventBanner(MultipartFile file) {
        return storeImage(file, EVENTS_DIR);
    }

    public String storeProfilePicture(MultipartFile file) {
        return storeImage(file, PROFILES_DIR);
    }

    public String storeFile(MultipartFile file) {
        return storeEventBanner(file);
    }

    @org.springframework.transaction.annotation.Transactional
    public void bindEventBanner(String key, Long eventId) {
        bindEventMedia(key, eventId, "EVENT_BANNER");
    }

    @org.springframework.transaction.annotation.Transactional
    public void bindEventCertificateTemplate(String key, Long eventId) {
        bindEventMedia(key, eventId, "EVENT_CERT_TEMPLATE");
    }

    private void bindEventMedia(String key, Long eventId, String resourceType) {
        if(key==null) return;
        if(access==null||eventId==null) throw new IllegalStateException("Authenticated event publication binding is required");
        access.requireEventManager(eventId);
        var asset=mediaAssetRepository.findByObjectKey(key).orElseThrow(com.college.events.security.DomainAccess::denied);
        if(!java.util.Objects.equals(asset.getOwnerUserId(),access.currentUser().getId())||asset.getResourceId()!=null
                ||asset.isLegalHold()||!"ACTIVE".equals(asset.getStatus())||asset.getExpiresAt()==null||!asset.getExpiresAt().isAfter(Instant.now()))
            throw com.college.events.security.DomainAccess.denied();
        asset.setResourceType(resourceType); asset.setResourceId(eventId); mediaAssetRepository.save(asset);
    }

    public String storeImage(MultipartFile file, String subDirectory) {
        if (!EVENTS_DIR.equals(subDirectory) && !PROFILES_DIR.equals(subDirectory)) {
            throw new IllegalArgumentException("Unsupported upload directory");
        }
        if (file == null || file.isEmpty()) {
            return null;
        }

        byte[] verifiedImage = validateImage(file);
        // Production injection requires a fresh authenticated account. Isolated storage tests
        // may create unbound metadata, but unbound objects are never deliverable by the gateway.
        Long ownerId = access == null ? null : access.currentUser().getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Upload transaction completion tracking is unavailable");
        }

        try {
            Path uploadPath = resolveSubDirectory(subDirectory);
            Files.createDirectories(uploadPath);
            Path actualRoot = Paths.get(uploadDir).toAbsolutePath().normalize().toRealPath();
            uploadPath = uploadPath.toRealPath();
            if (!uploadPath.startsWith(actualRoot)) throw new IOException("Upload directory escapes managed storage");

            String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
            String filename = UUID.randomUUID() + extension;
            Path targetPath = uploadPath.resolve(filename).normalize();
            if (!targetPath.startsWith(uploadPath)) {
                throw new IllegalArgumentException("Invalid upload path");
            }

            Files.write(targetPath, verifiedImage, java.nio.file.StandardOpenOption.CREATE_NEW);
            var createdFile = Files.readAttributes(targetPath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            String digest = sha256(targetPath);
            UploadIdentity identity = captureUploadIdentity(targetPath, actualRoot, createdFile.fileKey());
            registerRollbackCleanup(targetPath, uploadPath, identity, digest);

            String storedPath = subDirectory + "/" + filename;
            Instant acceptedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            try {
                mediaAssetRepository.save(MediaAsset.builder().objectKey(storedPath)
                        .ownerUserId(ownerId)
                        .mediaType(file.getContentType()).sizeBytes(Files.size(targetPath))
                        .uploadAcceptedAt(acceptedAt).expiresAt(acceptedAt.plus(MAX_RETENTION))
                        .sha256(digest).status("ACTIVE").build());
            } catch (RuntimeException metadataFailure) {
                cleanupUncommittedUpload(targetPath, uploadPath, identity, digest);
                throw new IllegalStateException("Upload metadata could not be committed", metadataFailure);
            } finally {
                if (!TransactionSynchronizationManager.isActualTransactionActive()) releaseIdentityAnchor(identity);
            }
            log.info("Stored file: {}", storedPath);
            return storedPath;
        } catch (IOException e) {
            log.error("Failed to store file", e);
            throw new IllegalStateException("Could not store file", e);
        }
    }

    private record UploadIdentity(Object fileKey, Path anchor) {}

    private UploadIdentity captureUploadIdentity(Path target, Path actualRoot, Object fileKey) throws IOException {
        if (fileKey != null) return new UploadIdentity(fileKey, null);
        // Windows providers may return null fileKey. A private hard link retains the exact
        // original identity without depending on creation time, pathname or equal contents.
        Path directory = actualRoot.resolve(".pending-upload-identities");
        Files.createDirectories(directory);
        directory = directory.toRealPath();
        if (!directory.startsWith(actualRoot)) throw new IOException("Upload identity directory escapes managed storage");
        Path anchor = directory.resolve(UUID.randomUUID().toString());
        try {
            Files.createLink(anchor, target);
        } catch (UnsupportedOperationException unsupported) {
            throw new IOException("Upload identity tracking is unavailable", unsupported);
        }
        return new UploadIdentity(null, anchor);
    }

    private void registerRollbackCleanup(Path target, Path originalParent, UploadIdentity identity, String digest) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                try {
                    if (status == STATUS_ROLLED_BACK) {
                        cleanupUncommittedUpload(target, originalParent, identity, digest);
                    } else if (status == STATUS_UNKNOWN) {
                        // Commit outcome is ambiguous: deleting here could destroy a committed asset.
                        log.error("Upload transaction outcome unknown; managed-media reconciliation required");
                    }
                } finally {
                    releaseIdentityAnchor(identity);
                }
            }
        });
    }

    /** Only compensates this newly-created object; never sweeps untracked or held files. */
    private void cleanupUncommittedUpload(Path target, Path originalParent, UploadIdentity identity, String digest) {
        try {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
            var current = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            boolean sameIdentity = identity.fileKey() != null ? identity.fileKey().equals(current.fileKey())
                    : identity.anchor() != null && !Files.isSymbolicLink(identity.anchor())
                    && Files.isSameFile(identity.anchor(), target);
            if (!current.isRegularFile() || !sameIdentity
                    || !target.getParent().toRealPath().equals(originalParent)
                    || !digest.equals(sha256(target))) {
                log.error("Uncommitted upload identity changed; managed-media reconciliation required");
                return;
            }
            Files.delete(target);
        } catch (IOException | RuntimeException cleanupFailure) {
            // Do not mask the original transaction failure or disclose a filesystem pathname.
            log.error("Uncommitted upload cleanup failed; managed-media reconciliation required");
        }
    }

    private void releaseIdentityAnchor(UploadIdentity identity) {
        if (identity.anchor() == null) return;
        try {
            Path anchor = identity.anchor();
            // anchor was constructed under the resolved real directory at capture time.
            // Do not follow a subsequently replaced directory link outside that custody.
            if (!Files.exists(anchor, LinkOption.NOFOLLOW_LINKS)) return;
            var attributes = Files.readAttributes(anchor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || !anchor.getParent().toRealPath().equals(anchor.getParent())) {
                log.error("Upload identity anchor custody changed; managed-media reconciliation required");
                return;
            }
            Files.delete(anchor);
        } catch (IOException | RuntimeException failure) {
            log.error("Upload identity anchor cleanup failed; managed-media reconciliation required");
        }
    }

    public void deleteFile(String filename) {
        if (filename == null || filename.isEmpty()) return;
        var asset = mediaAssetRepository.findByObjectKey(filename).orElse(null);
        if (asset == null || asset.isLegalHold()) return; // preserve untracked and held evidence
        if (access != null && !java.util.Objects.equals(asset.getOwnerUserId(), access.currentUser().getId()))
            throw com.college.events.security.DomainAccess.denied();
        try {
            Path resolvedPath = resolveExistingPath(filename);
            if (resolvedPath != null) {
                Files.deleteIfExists(resolvedPath);
            }
            asset.setStatus("PURGED"); asset.setPurgedAt(Instant.now()); mediaAssetRepository.save(asset);
        } catch (IOException e) {
            log.warn("Could not delete file: {}", filename);
        }
    }

    public long deleteMediaOlderThan(Duration retention) {
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadRoot)) {
            return 0;
        }

        Instant now = Instant.now();
        // The institutional contract is a hard maximum. A caller/configuration
        // cannot extend a file's deadline by passing a longer duration.
        final long[] deleted = {0};
        for (MediaAsset asset : expiredAssets(now)) {
            if (asset.isLegalHold() && (asset.getLegalHoldUntil() == null || asset.getLegalHoldUntil().isAfter(now))) continue;
            try {
                Path assetPath = resolveExistingPath(asset.getObjectKey());
                if (assetPath != null) {
                    Files.deleteIfExists(assetPath);
                    deleted[0]++;
                }
                asset.setStatus("PURGED");
                asset.setPurgedAt(now);
                asset.setLastPurgeError(null);
                mediaAssetRepository.save(asset);
            } catch (IOException e) {
                asset.setStatus("PURGE_FAILED");
                asset.setPurgeAttempts(asset.getPurgeAttempts() + 1);
                String message = e.getMessage() == null ? "object deletion failed" : e.getMessage();
                asset.setLastPurgeError(message.length() > 240 ? message.substring(0, 240) : message);
                mediaAssetRepository.save(asset);
                log.warn("Could not delete expired managed media asset id={}; retry scheduled", asset.getId(), e);
            }
        }
        // Never infer expiry from filesystem timestamps and never delete an
        // untracked path: that could remove a held asset or unrelated data.
        // Orphan cleanup requires a separate inventory/reconciliation workflow.
        return deleted[0];
    }

    private List<MediaAsset> expiredAssets(Instant now) {
        List<MediaAsset> active = mediaAssetRepository
                .findByExpiresAtLessThanEqualAndStatusOrderByExpiresAtAsc(now, "ACTIVE");
        List<MediaAsset> failed = mediaAssetRepository
                .findByExpiresAtLessThanEqualAndStatusOrderByExpiresAtAsc(now, "PURGE_FAILED");
        return java.util.stream.Stream.concat(active.stream(), failed.stream()).toList();
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private byte[] validateImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPG, PNG, and WebP images are supported");
        }

        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Image must be less than 5 MB");
        }

        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only JPG, PNG, and WebP images are supported");
        }
        String format = "image/png".equalsIgnoreCase(contentType) ? "png" : "image/jpeg".equalsIgnoreCase(contentType) ? "jpeg" : "webp";
        if (!(format.equals("png") && extension.equals(".png") || format.equals("jpeg") && (extension.equals(".jpg")||extension.equals(".jpeg")) || format.equals("webp") && extension.equals(".webp")))
            throw new IllegalArgumentException("Image content type and extension must match");
        try (var input=file.getInputStream()) {
            byte[] bytes=input.readNBytes((int)MAX_SIZE+1);
            if(bytes.length>MAX_SIZE) throw new IllegalArgumentException("Image exceeds the allowed size");
            try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
                var readers=javax.imageio.ImageIO.getImageReaders(stream);
                if(!readers.hasNext()) throw new IllegalArgumentException("Image content cannot be decoded by this release");
                var reader=readers.next();
                try {
                    reader.setInput(stream,true,true);
                    String decoded=reader.getFormatName().toLowerCase(Locale.ROOT);
                    if(!(decoded.equals(format)||format.equals("jpeg")&&decoded.equals("jpg"))) throw new IllegalArgumentException("Image content does not match its declared type");
                    int width=reader.getWidth(0),height=reader.getHeight(0);
                    if(width<1||height<1||width>8192||height>8192||(long)width*height>16_000_000) throw new IllegalArgumentException("Image dimensions exceed the allowed limits");
                    var image=reader.read(0);
                    var output=new java.io.ByteArrayOutputStream();
                    if(!javax.imageio.ImageIO.write(image,format,output)||output.size()>MAX_SIZE)
                        throw new IllegalArgumentException("Image cannot be safely encoded within the allowed size");
                    // Store a clean encoded image, stripping embedded metadata and trailing payloads.
                    return output.toByteArray();
                } finally {reader.dispose();}
            }
        } catch(IOException invalid) {throw new IllegalArgumentException("Invalid image content",invalid);}
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        }

        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return ".jpg";
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return ".png";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return ".webp";
        }
        throw new IllegalArgumentException("Only JPG, PNG, and WebP images are supported");
    }

    private Path resolveSubDirectory(String subDirectory) {
        return Paths.get(uploadDir).toAbsolutePath().normalize().resolve(subDirectory).normalize();
    }

    private Path resolveExistingPath(String filename) throws IOException {
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (filename == null || filename.isBlank()) throw new IOException("Invalid managed object key");
        if (!Files.exists(uploadRoot)) return null;
        Path actualRoot = uploadRoot.toRealPath();
        List<Path> candidates = List.of(
            uploadRoot.resolve(filename).normalize(),
            uploadRoot.resolve(EVENTS_DIR).resolve(filename).normalize(),
            uploadRoot.resolve(PROFILES_DIR).resolve(filename).normalize()
        );

        for (Path candidate : candidates) {
            if (!candidate.startsWith(uploadRoot)) throw new IOException("Managed object escapes storage");
            if (Files.exists(candidate)) {
                if (!candidate.toRealPath().startsWith(actualRoot)) throw new IOException("Managed object escapes storage");
                return candidate;
            }
        }
        return null;
    }
}
