package dev.jevguard.core.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.jevguard.core.jev.JevApiException;
import dev.jevguard.core.jev.JevTransport;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ModerationBatcherTest {
    /** Answers "harassment" for messages containing "idiot", "safe" otherwise. */
    static final class FakeJev implements JevTransport {
        final List<JsonObject> requests = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<JsonObject> evaluate(JsonObject request) {
            requests.add(request);
            var messages = request.getAsJsonObject("state").getAsJsonArray("messages");
            JsonObject answers = new JsonObject();
            for (int i = 0; i < messages.size(); i++) {
                boolean bad = messages.get(i).getAsString().contains("idiot");
                JsonObject probs = new JsonObject();
                probs.addProperty("safe", bad ? 0.05 : 0.95);
                probs.addProperty("harassment", bad ? 0.95 : 0.05);
                JsonObject answer = new JsonObject();
                answer.addProperty("type", "choice");
                answer.addProperty("choice", bad ? "harassment" : "safe");
                answer.add("probabilities", probs);
                answer.addProperty("confidence", 0.9);
                answers.add("m" + i, answer);
            }
            JsonObject response = new JsonObject();
            response.add("answers", answers);
            return CompletableFuture.completedFuture(response);
        }
    }

    private static CircuitBreaker breaker() {
        return new CircuitBreaker(2, Duration.ofMinutes(1), Clock.systemUTC());
    }

    @Test
    void fullBatchIsSentAsOneRequestAndAnswersMapBack() throws Exception {
        FakeJev jev = new FakeJev();
        try (var batcher = new ModerationBatcher(jev, Fixtures.settings(3, Duration.ofSeconds(10)), breaker())) {
            var a = batcher.submit("gg");
            var b = batcher.submit("you idiot");
            var c = batcher.submit("nice base");

            assertEquals("safe", a.get(1, TimeUnit.SECONDS).choice());
            assertEquals("harassment", b.get(1, TimeUnit.SECONDS).choice());
            assertEquals("safe", c.get(1, TimeUnit.SECONDS).choice());
            assertEquals(1, jev.requests.size());
        }
    }

    @Test
    void partialBatchFlushesAfterMaxWait() throws Exception {
        FakeJev jev = new FakeJev();
        try (var batcher = new ModerationBatcher(jev, Fixtures.settings(8, Duration.ofMillis(20)), breaker())) {
            assertEquals("safe", batcher.submit("hello").get(1, TimeUnit.SECONDS).choice());
            assertEquals(1, jev.requests.size());
        }
    }

    @Test
    void repeatedFailuresOpenTheCircuit() {
        JevTransport failing = req -> CompletableFuture.failedFuture(new JevApiException(529, "overloaded"));
        CircuitBreaker breaker = breaker();
        try (var batcher = new ModerationBatcher(failing, Fixtures.settings(1, Duration.ZERO), breaker)) {
            for (int i = 0; i < 2; i++) {
                var f = batcher.submit("hi");
                assertThrows(ExecutionException.class, () -> f.get(1, TimeUnit.SECONDS));
            }
            assertTrue(breaker.isOpen());
            var skipped = batcher.submit("hi");
            var e = assertThrows(ExecutionException.class, () -> skipped.get(1, TimeUnit.SECONDS));
            assertInstanceOf(ModerationUnavailableException.class, e.getCause());
        }
    }
}
