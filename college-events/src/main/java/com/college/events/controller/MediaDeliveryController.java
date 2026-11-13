package com.college.events.controller;

import com.college.events.repository.MediaAssetRepository;
import com.college.events.security.DomainAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.time.Instant;
import java.io.IOException;

/** Private local-storage bridge. No static handler, untracked paths, or public URL authority. */
@RestController @RequiredArgsConstructor
public class MediaDeliveryController {
    private final MediaAssetRepository assets;
    private final com.college.events.service.MediaAccessService access;
    @Value("${app.upload.dir:./uploads}") private String uploadDir;

    @GetMapping("/uploads/{directory}/{filename}")
    public ResponseEntity<byte[]> image(@PathVariable String directory,@PathVariable String filename) throws IOException {
        if(!directory.matches("events|profiles")||!filename.matches("[a-zA-Z0-9-]+\\.(png|jpg|jpeg|webp)")) throw missing();
        var asset=assets.findByObjectKey(directory+"/"+filename).orElseThrow(MediaDeliveryController::missing);
        if(!access.mayRead(asset)) throw missing();
        if(asset.isLegalHold()||!"ACTIVE".equals(asset.getStatus())||asset.getExpiresAt()==null||!asset.getExpiresAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.GONE,"Media is unavailable");
        if(!java.util.Set.of("image/png","image/jpeg","image/webp").contains(asset.getMediaType())||asset.getSizeBytes()>5*1024*1024) throw missing();
        Path root=Paths.get(uploadDir).toAbsolutePath().normalize().toRealPath();
        Path path=root.resolve(asset.getObjectKey()).normalize();
        if(!path.startsWith(root)||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw missing();
        Path actual=path.toRealPath(); if(!actual.startsWith(root)||Files.size(actual)>5*1024*1024) throw missing();
        byte[] bytes=Files.readAllBytes(actual);
        // Revalidate at the delivery boundary after disk I/O; no TTL/cache can extend expiry.
        var current=assets.findByObjectKey(asset.getObjectKey()).orElseThrow(MediaDeliveryController::missing);
        if(!access.mayRead(current)) throw missing();
        if(current.isLegalHold()||!"ACTIVE".equals(current.getStatus())||current.getExpiresAt()==null||!current.getExpiresAt().isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.GONE);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).header("X-Content-Type-Options","nosniff")
                .contentType(MediaType.parseMediaType(asset.getMediaType())).body(bytes);
    }
    /** Internal ID-bound controllers may resolve an opaque business-resource route without returning this key to clients. */
    public ResponseEntity<byte[]> deliverEventObjectKey(String objectKey) throws IOException {
        if(objectKey==null||!objectKey.matches("events/[a-zA-Z0-9-]+\\.(png|jpg|jpeg|webp)")) throw missing();
        return image("events",objectKey.substring("events/".length()));
    }
    private static ResponseStatusException missing() {return new ResponseStatusException(HttpStatus.NOT_FOUND);}
}
