package dev.jevguard.core.moderation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ResponseParser {
    private ResponseParser() {
    }

    public static Verdict parse(JsonObject response, String questionId) {
        JsonObject answers = response.getAsJsonObject("answers");
        if (answers == null || !answers.has(questionId)) {
            throw new IllegalStateException("Response has no answer for " + questionId);
        }
        JsonObject answer = answers.getAsJsonObject(questionId);
        Map<String, Double> probabilities = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : answer.getAsJsonObject("probabilities").entrySet()) {
            probabilities.put(e.getKey(), e.getValue().getAsDouble());
        }
        double confidence = answer.has("confidence") ? answer.get("confidence").getAsDouble() : 0.0;
        return new Verdict(answer.get("choice").getAsString(), confidence, Map.copyOf(probabilities));
    }
}
