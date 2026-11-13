package com.college.events.service;

import com.college.events.controller.MediaDeliveryController;
import com.college.events.model.MediaAsset;
import com.college.events.repository.MediaAssetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic file bytes only; actual ImageIO and local filesystem boundaries. */
class MediaAdversarialBoundaryTest {
    @TempDir Path temp;
    final MediaAssetRepository repository=mock(MediaAssetRepository.class);
    FileStorageService storage() {
        var service=new FileStorageService(repository);
        ReflectionTestUtils.setField(service,"uploadDir",temp.resolve("managed").toString());
        return service;
    }
    byte[] png(int width,int height) throws Exception {
        var out=new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",out);
        return out.toByteArray();
    }
    MockMultipartFile image(byte[] bytes) { return new MockMultipartFile("file","test.png","image/png",bytes); }
    @Test void rejectsTraversalAbsoluteAndUnknownDirectoryBeforeWriting() throws Exception {
        var service=storage();
        for(String directory:List.of("../escape","events/../../escape",temp.toString(),"events/child","", "EVENTS"))
            assertThrows(IllegalArgumentException.class,()->service.storeImage(image(png(1,1)),directory));
        verifyNoInteractions(repository); assertFalse(Files.exists(temp.resolve("managed")));
    }
    @Test void filenameTraversalCannotControlGeneratedObjectKey() throws Exception {
        String key=storage().storeImage(new MockMultipartFile("file","../../synthetic.png","image/png",png(1,1)),"events");
        assertTrue(key.matches("events/[0-9a-f-]+\\.png"));
        assertTrue(Files.isRegularFile(temp.resolve("managed").resolve(key)));
    }
    @Test void rejectsSvgArchiveMismatchedAndTruncatedInput() throws Exception {
        var service=storage();
        for(byte[] bytes:List.of("<svg onload='synthetic'/>".getBytes(),new byte[]{'P','K',3,4},Arrays.copyOf(png(1,1),20)))
            assertThrows(IllegalArgumentException.class,()->service.storeImage(image(bytes),"events"));
        assertThrows(IllegalArgumentException.class,()->service.storeImage(new MockMultipartFile("file","test.jpg","image/jpeg",png(1,1)),"events"));
        verifyNoInteractions(repository);
    }
    @Test void actualStreamLimitRejectsDishonestMultipartLength() {
        var file=new MockMultipartFile("file","test.png","image/png",new byte[5*1024*1024+1]) {
            @Override public long getSize(){return 1;}
        };
        assertThrows(IllegalArgumentException.class,()->storage().storeImage(file,"events"));
        verifyNoInteractions(repository);
    }
    @Test void rejectsOversizedDimensionsBeforeFullDecode() throws Exception {
        assertThrows(IllegalArgumentException.class,()->storage().storeImage(image(png(8193,1)),"events"));
        verifyNoInteractions(repository);
    }
    @Test void trailingActivePayloadIsStrippedByActualReencoding() throws Exception {
        var bytes=new ByteArrayOutputStream(); bytes.write(png(2,2));
        String marker="<script>SYNTHETIC_TRAILING_PAYLOAD</script>"; bytes.write(marker.getBytes());
        String key=storage().storeImage(image(bytes.toByteArray()),"events");
        byte[] stored=Files.readAllBytes(temp.resolve("managed").resolve(key));
        assertFalse(new String(stored,java.nio.charset.StandardCharsets.ISO_8859_1).contains(marker));
        assertNotNull(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(stored)));
    }
    @Test void metadataFailureRemovesWrittenObject() throws Exception {
        when(repository.save(any())).thenThrow(new IllegalStateException("synthetic metadata failure"));
        assertThrows(IllegalStateException.class,()->storage().storeImage(image(png(1,1)),"events"));
        try(var files=Files.walk(temp.resolve("managed"))) {assertEquals(0,files.filter(Files::isRegularFile).count());}
    }
    @Test void storageFailureDoesNotExposeFilesystemPathInUserMessage() throws Exception {
        Files.write(temp.resolve("managed"),new byte[]{1});
        var failure=assertThrows(IllegalStateException.class,()->storage().storeImage(image(png(1,1)),"events"));
        assertEquals("Could not store file",failure.getMessage());
        assertFalse(failure.getMessage().contains(temp.toString()));
        verifyNoInteractions(repository);
    }
    @Test void corruptTraversalMetadataCannotPurgeOutsideAndRecordsFailure() throws Exception {
        Files.createDirectories(temp.resolve("managed")); Path outside=Files.write(temp.resolve("outside.png"),new byte[]{7});
        var asset=MediaAsset.builder().objectKey("../outside.png").expiresAt(Instant.EPOCH).status("ACTIVE").build();
        when(repository.findByExpiresAtLessThanEqualAndStatusOrderByExpiresAtAsc(any(),eq("ACTIVE"))).thenReturn(List.of(asset));
        when(repository.findByExpiresAtLessThanEqualAndStatusOrderByExpiresAtAsc(any(),eq("PURGE_FAILED"))).thenReturn(List.of());
        assertEquals(0,storage().deleteMediaOlderThan(Duration.ofDays(30)));
        assertTrue(Files.exists(outside)); assertEquals("PURGE_FAILED",asset.getStatus()); assertEquals(1,asset.getPurgeAttempts());
    }
    @Test void deliveryRechecksExpiryAndAuthorizationAfterDiskRead() throws Exception {
        Path file=Files.createDirectories(temp.resolve("events")).resolve("test.png"); Files.write(file,png(1,1));
        var first=MediaAsset.builder().objectKey("events/test.png").mediaType("image/png").status("ACTIVE").expiresAt(Instant.now().plusSeconds(60)).build();
        var expired=MediaAsset.builder().objectKey("events/test.png").mediaType("image/png").status("ACTIVE").expiresAt(Instant.EPOCH).build();
        var access=mock(MediaAccessService.class); when(access.mayRead(any())).thenReturn(true);
        var controller=new MediaDeliveryController(repository,access); ReflectionTestUtils.setField(controller,"uploadDir",temp.toString());
        when(repository.findByObjectKey("events/test.png")).thenReturn(Optional.of(first),Optional.of(expired));
        assertEquals(410,assertThrows(ResponseStatusException.class,()->controller.image("events","test.png")).getStatusCode().value());
        when(repository.findByObjectKey("events/test.png")).thenReturn(Optional.of(first));
        when(access.mayRead(first)).thenReturn(true,false);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->controller.image("events","test.png")).getStatusCode().value());
    }
}
