package dev.jevguard.core.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RequestBuilderTest {
    @Test
    void buildsOneChoicePerMessageWithSafeOptionFirst() {
        JsonObject req = RequestBuilder.build(Fixtures.settings(8, Duration.ZERO), List.of("gg", "hi"));

        assertEquals("jev-latest", req.get("model").getAsString());
        assertEquals(2, req.getAsJsonObject("state").getAsJsonArray("messages").size());

        JsonObject questions = req.getAsJsonObject("questions");
        assertEquals(2, questions.size());
        JsonObject m1 = questions.getAsJsonObject("m1");
        assertEquals("choice", m1.get("type").getAsString());
        assertTrue(m1.get("instructions").getAsString().contains("Minecraft chat message at `messages[1]`"));
        assertEquals(List.of("safe", "harassment", "grooming"), List.copyOf(m1.getAsJsonObject("criteria").keySet()));
    }
}
