package dev.jevguard.core.moderation;

import com.google.gson.JsonObject;
import dev.jevguard.core.jev.JevTransport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Groups chat messages into one API request per batch. A batch is sent when it is full
 * or when its oldest message has waited {@code maxBatchWait}, whichever comes first.
 */
public final class ModerationBatcher implements AutoCloseable {
    private record Pending(String text, CompletableFuture<Verdict> result) {
    }

    private final JevTransport transport;
    private final ModerationSettings settings;
    private final CircuitBreaker breaker;
    private final ScheduledExecutorService scheduler;
    private final List<Pending> batch = new ArrayList<>();
    private ScheduledFuture<?> scheduledFlush;

    public ModerationBatcher(JevTransport transport, ModerationSettings settings, CircuitBreaker breaker) {
        this.transport = transport;
        this.settings = settings;
        this.breaker = breaker;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jev-guard-batcher");
            t.setDaemon(true);
            return t;
        });
    }

    public CompletableFuture<Verdict> submit(String text) {
        if (!breaker.allowRequest()) {
            return CompletableFuture.failedFuture(new ModerationUnavailableException("circuit breaker open"));
        }
        CompletableFuture<Verdict> result = new CompletableFuture<>();
        synchronized (batch) {
            batch.add(new Pending(text, result));
            if (batch.size() >= settings.batchSize()) {
                flushLocked();
            } else if (scheduledFlush == null) {
                scheduledFlush = scheduler.schedule(this::flush, settings.maxBatchWait().toMillis(), TimeUnit.MILLISECONDS);
            }
        }
        return result;
    }

    private void flush() {
        synchronized (batch) {
            flushLocked();
        }
    }

    private void flushLocked() {
        if (scheduledFlush != null) {
            scheduledFlush.cancel(false);
            scheduledFlush = null;
        }
        if (batch.isEmpty()) {
            return;
        }
        List<Pending> sending = List.copyOf(batch);
        batch.clear();

        JsonObject request = RequestBuilder.build(settings, sending.stream().map(Pending::text).toList());
        transport.evaluate(request).whenComplete((response, error) -> {
            if (error != null) {
                breaker.recordFailure();
                sending.forEach(p -> p.result().completeExceptionally(error));
                return;
            }
            breaker.recordSuccess();
            for (int i = 0; i < sending.size(); i++) {
                Pending p = sending.get(i);
                try {
                    p.result().complete(ResponseParser.parse(response, RequestBuilder.questionId(i)));
                } catch (RuntimeException e) {
                    p.result().completeExceptionally(e);
                }
            }
        });
    }

    @Override
    public void close() {
        flush();
        scheduler.shutdown();
    }
}
