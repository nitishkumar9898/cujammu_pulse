package com.college.events.service;

import com.college.events.security.BoundedResponseBodyHandler;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic response-subscriber regression; transport integration is a separate CI gate. */
class BoundedProviderResponseBodySubscriberTest {
    @Test void oversizedChunkFailsForByteBudgetAndCancelsUpstream() {
        HttpResponse.BodySubscriber<byte[]> subscriber = BoundedResponseBodyHandler.ofByteArray(65_536).apply(null);
        boolean[] cancelled = {false};
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override public void request(long count) {}
            @Override public void cancel() { cancelled[0] = true; }
        });
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[100_000])));

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> subscriber.getBody().toCompletableFuture().get());
        assertInstanceOf(IOException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("byte budget"));
        assertTrue(cancelled[0], "The upstream body publisher must be cancelled when the budget is exceeded");
    }
}
