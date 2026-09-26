package dev.jevguard.core.jev;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

/** Sends one System One request and returns the raw JSON response body. */
@FunctionalInterface
public interface JevTransport {
    CompletableFuture<JsonObject> evaluate(JsonObject request);
}
