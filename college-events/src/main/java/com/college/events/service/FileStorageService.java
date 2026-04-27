package com.college.events.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private static final String EVENTS_DIR = "events";
    private static final String PROFILES_DIR = "profiles";
    private static final List<String> ALLOWED_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/webp"
    );
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".webp");
    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB

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

    public String storeImage(MultipartFile file, String subDirectory) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        validateImage(file);

        try {
            Path uploadPath = resolveSubDirectory(subDirectory);
            Files.createDirectories(uploadPath);

            String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
            String filename = UUID.randomUUID() + extension;
            Path targetPath = uploadPath.resolve(filename).normalize();

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            String storedPath = subDirectory + "/" + filename;
            log.info("Stored file: {}", storedPath);
            return storedPath;
        } catch (IOException e) {
            log.error("Failed to store file", e);
            throw new RuntimeException("Could not store file: " + e.getMessage());
        }
    }

    public void deleteFile(String filename) {
        if (filename == null || filename.isEmpty()) return;
        try {
            Path resolvedPath = resolveExistingPath(filename);
            if (resolvedPath != null) {
                Files.deleteIfExists(resolvedPath);
            }
        } catch (IOException e) {
            log.warn("Could not delete file: {}", filename);
        }
    }

    private void validateImage(MultipartFile file) {
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

    private Path resolveExistingPath(String filename) {
        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
            uploadRoot.resolve(filename).normalize(),
            uploadRoot.resolve(EVENTS_DIR).resolve(filename).normalize(),
            uploadRoot.resolve(PROFILES_DIR).resolve(filename).normalize()
        );

        for (Path candidate : candidates) {
            if (candidate.startsWith(uploadRoot) && Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
