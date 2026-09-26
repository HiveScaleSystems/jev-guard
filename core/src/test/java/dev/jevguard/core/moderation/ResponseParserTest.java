package dev.jevguard.core.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ResponseParserTest {
    // Shape taken from https://docs.typesafe.ai/api.md
    static final String RESPONSE = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "m0": {
                  "type": "choice",
                  "choice": "harassment",
                  "probabilities": { "safe": 0.08, "harassment": 0.9, "grooming": 0.02 },
                  "confidence": 0.81
                }
              },
              "usage": { "input_tokens": 318, "output_tokens": 34 }
            }""";

    @Test
    void parsesChoiceAnswer() {
        Verdict v = ResponseParser.parse(JsonParser.parseString(RESPONSE).getAsJsonObject(), "m0");
        assertEquals("harassment", v.choice());
        assertEquals(0.81, v.confidence());
        assertEquals(0.9, v.probability("harassment"));
        assertEquals(0.0, v.probability("unknown"));
    }

    @Test
    void missingAnswerFails() {
        JsonObject response = JsonParser.parseString(RESPONSE).getAsJsonObject();
        assertThrows(IllegalStateException.class, () -> ResponseParser.parse(response, "m1"));
    }
}
