package com.college.events.security;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Limits actual response bytes before the JDK collector retains a chunk. */
public final class BoundedResponseBodyHandler {
    private BoundedResponseBodyHandler() {}

    public static HttpResponse.BodyHandler<byte[]> ofByteArray(int maximumBytes) {
        if (maximumBytes < 1) throw new IllegalArgumentException("Positive response budget required");
        return ignored -> new HttpResponse.BodySubscriber<>() {
            private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
            private Flow.Subscription subscription;
            private long received;
            private boolean finished;

            @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
            @Override public void onSubscribe(Flow.Subscription incoming) {
                subscription = incoming;
                delegate.onSubscribe(incoming);
            }
            @Override public void onNext(List<ByteBuffer> buffers) {
                if (finished) return;
                for (ByteBuffer buffer : buffers) {
                    received += buffer.remaining();
                    if (received > maximumBytes) {
                        finished = true;
                        subscription.cancel();
                        delegate.onError(new IOException("Response exceeds configured byte budget"));
                        return;
                    }
                }
                delegate.onNext(buffers);
            }
            @Override public void onError(Throwable error) {
                if (!finished) { finished = true; delegate.onError(error); }
            }
            @Override public void onComplete() {
                if (!finished) { finished = true; delegate.onComplete(); }
            }
        };
    }
}
