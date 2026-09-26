package dev.jevguard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import dev.jevguard.core.ChatModerator.ChatResult;
import dev.jevguard.core.jev.ApiTarget;
import dev.jevguard.core.jev.JevTransport;
import dev.jevguard.core.moderation.Category;
import dev.jevguard.core.moderation.CircuitBreaker;
import dev.jevguard.core.moderation.ModerationBatcher;
import dev.jevguard.core.moderation.ModerationSettings;
import dev.jevguard.core.moderation.Policy;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class ChatModeratorTest {
    static final Category HARASSMENT = new Category("harassment", "Insults", 0.8, List.of("cancel", "warn", "notify-staff"));

    /** Records everything the moderator sends. */
    static final class Recorder implements Platform, ChatSender {
        final List<String> staff = new CopyOnWriteArrayList<>();
        final List<String> player = new CopyOnWriteArrayList<>();

        @Override
        public void broadcast(String permission, String template, Map<String, String> ph) {
            staff.add(permission + " " + template + " " + ph);
        }

        @Override
        public void runConsoleCommand(String command) {
        }

        @Override
        public Logger logger() {
            return Logger.getLogger("test");
        }

        @Override
        public String name() {
            return "Steve";
        }

        @Override
        public UUID uuid() {
            return UUID.nameUUIDFromBytes(new byte[0]);
        }

        @Override
        public boolean hasPermission(String node) {
            return false;
        }

        @Override
        public void kick(String template, Map<String, String> ph) {
        }

        @Override
        public void send(String template, Map<String, String> ph) {
            player.add(template);
        }
    }

    /** Harassment for messages containing "idiot"; optionally slow. */
    static JevTransport fakeJev(long delayMs) {
        return request -> CompletableFuture.supplyAsync(() -> {
            var messages = request.getAsJsonObject("state").getAsJsonArray("messages");
            JsonObject answers = new JsonObject();
            for (int i = 0; i < messages.size(); i++) {
                boolean bad = messages.get(i).getAsString().contains("idiot");
                JsonObject probs = new JsonObject();
                probs.addProperty("safe", bad ? 0.06 : 0.97);
                probs.addProperty("harassment", bad ? 0.94 : 0.03);
                JsonObject answer = new JsonObject();
                answer.addProperty("choice", bad ? "harassment" : "safe");
                answer.add("probabilities", probs);
                answer.addProperty("confidence", 0.9);
                answers.add("m" + i, answer);
            }
            JsonObject response = new JsonObject();
            response.add("answers", answers);
            return response;
        }, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS));
    }

    static ChatModerator moderator(Recorder r, Mode mode, long jevDelayMs, long holdTimeoutMs) {
        ModerationSettings settings = new ModerationSettings("Minecraft", "jev-latest", "Ordinary chat.", List.of(HARASSMENT), 0.5,
                1, Duration.ZERO);
        GuardConfig config = new GuardConfig(ApiTarget.typesafe("https://x", "k"), Duration.ofSeconds(1), 0, mode,
                true, Duration.ofMillis(holdTimeoutMs), 2, false, 5, Duration.ofSeconds(30), settings,
                new GuardConfig.Messages("warn", "kick", "unavailable", "blocked <probability>", "flag", "review"));
        CircuitBreaker breaker = new CircuitBreaker(5, Duration.ofSeconds(30), Clock.systemUTC());
        return new ChatModerator(config, new ModerationBatcher(fakeJev(jevDelayMs), settings, breaker),
                new Policy(settings), r, new Stats());
    }

    @Test
    void holdBlocksFlaggedMessageAndTellsStaffOnce() throws Exception {
        Recorder r = new Recorder();
        ChatResult result = moderator(r, Mode.HOLD, 10, 2000).check(r, "you idiot", true).get(3, TimeUnit.SECONDS);

        assertEquals(ChatResult.BLOCK, result);
        assertEquals(1, r.staff.size(), "notify-staff is replaced by the blocked alert");
        assertTrue(r.staff.getFirst().startsWith(Permissions.NOTIFY + " blocked"));
        assertTrue(r.staff.getFirst().contains("probability=94%"));
        assertEquals(List.of("warn"), r.player);
    }

    @Test
    void holdAllowsSafeMessage() throws Exception {
        Recorder r = new Recorder();
        assertEquals(ChatResult.ALLOW, moderator(r, Mode.HOLD, 10, 2000).check(r, "gg", true).get(3, TimeUnit.SECONDS));
        assertTrue(r.staff.isEmpty());
    }

    @Test
    void holdTimeoutFailsOpenThenStillActsOnLateVerdict() throws Exception {
        Recorder r = new Recorder();
        ChatResult result = moderator(r, Mode.HOLD, 300, 50).check(r, "you idiot", true).get(3, TimeUnit.SECONDS);
        assertEquals(ChatResult.ALLOW, result);

        Thread.sleep(500);
        assertEquals(1, r.staff.size());
        assertTrue(r.staff.getFirst().startsWith(Permissions.NOTIFY + " flag"), "late verdict can't block, so it's a flag");
    }

    @Test
    void shadowNeverActs() throws Exception {
        Recorder r = new Recorder();
        assertEquals(ChatResult.ALLOW, moderator(r, Mode.SHADOW, 0, 2000).check(r, "you idiot", true).get());
        Thread.sleep(200);
        assertTrue(r.staff.isEmpty());
        assertTrue(r.player.isEmpty());
    }
}
