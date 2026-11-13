package com.college.events.service;

import com.college.events.security.BoundedResponseBodyHandler;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;

class BoundedProviderResponseTest {
    @Test void exactBudgetAcceptedAndNextByteCancelsUpstream() throws Exception {
        var subscription = new Flow.Subscription() {
            boolean cancelled;
            public void request(long count) {}
            public void cancel() { cancelled = true; }
        };
        var subscriber = BoundedResponseBodyHandler.ofByteArray(4).apply(null);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}), ByteBuffer.wrap(new byte[]{3,4})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{5})));
        assertTrue(subscription.cancelled);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> subscriber.getBody().toCompletableFuture().get());
        var exact = BoundedResponseBodyHandler.ofByteArray(4).apply(null);
        exact.onSubscribe(subscription);
        exact.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2,3,4})));
        exact.onComplete();
        assertArrayEquals(new byte[]{1,2,3,4}, exact.getBody().toCompletableFuture().get());
    }

}
