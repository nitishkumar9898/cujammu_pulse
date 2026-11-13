package com.college.events.service;

import com.college.events.config.MediaExpiryInterceptor;
import com.college.events.model.MediaAsset;
import com.college.events.model.User;
import com.college.events.repository.MediaAssetRepository;
import com.college.events.security.DomainAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaDeliveryContainmentTest {
    final MediaAssetRepository assets=mock(MediaAssetRepository.class);
    final MediaAccessService access=mock(MediaAccessService.class);
    final MediaExpiryInterceptor guard=new MediaExpiryInterceptor(assets,access);
    final MockHttpServletRequest request=new MockHttpServletRequest("GET","/uploads/profiles/image.png");
    final MockHttpServletResponse response=new MockHttpServletResponse();
    MediaAsset active() {
        var asset=MediaAsset.builder().objectKey("profiles/image.png").ownerUserId(1L)
                .uploadAcceptedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).status("ACTIVE").build();
        when(assets.findByObjectKey("profiles/image.png")).thenReturn(Optional.of(asset));
        when(access.mayRead(asset)).thenReturn(true); return asset;
    }
    @Test void unknownObjectDeniedWithoutConsultingOwner() {
        when(assets.findByObjectKey("profiles/image.png")).thenReturn(Optional.empty());
        assertFalse(guard.preHandle(request,response,new Object())); assertEquals(404,response.getStatus());
        verifyNoInteractions(access);
    }
    @Test void authenticatedStrangerCannotReadKnownObject() {
        var asset=active(); when(access.mayRead(asset)).thenReturn(false);
        assertFalse(guard.preHandle(request,response,new Object())); assertEquals(404,response.getStatus());
    }
    @Test void heldExpiredObjectCannotUseOrdinaryDelivery() {
        var asset=active(); asset.setExpiresAt(Instant.EPOCH); asset.setLegalHold(true); asset.setLegalHoldUntil(null);
        assertFalse(guard.preHandle(request,response,new Object())); assertEquals(410,response.getStatus());
    }
    @Test void purgedObjectCannotBeResurrectedByRemainingLocalBytes() {
        active().setStatus("PURGED");
        assertFalse(guard.preHandle(request,response,new Object())); assertEquals(410,response.getStatus());
    }
    @Test void authorizedOwnerGetsUnexpiredDeliveryWithNoStore() {
        active(); request.setMethod("HEAD");
        assertTrue(guard.preHandle(request,response,new Object()));
        assertEquals("no-store, private",response.getHeader("Cache-Control"));
        assertEquals("nosniff",response.getHeader("X-Content-Type-Options"));
    }
    @Test void unboundHistoricalOwnerIsDenied() {
        var asset=active(); asset.setOwnerUserId(null); when(access.mayRead(asset)).thenReturn(false);
        assertFalse(guard.preHandle(request,response,new Object())); assertEquals(404,response.getStatus());
    }
}
