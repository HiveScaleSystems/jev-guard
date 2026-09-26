package dev.jevguard.core.jev;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Where System One requests go and how they are wrapped. Requests are always built in
 * TypeSafe's native shape ({@code model}, {@code state}, {@code questions}); each target
 * adapts that shape to its API and unwraps the response back to it.
 *
 * @param uri             endpoint to POST to
 * @param headers         extra headers, including auth
 * @param wrapRequest     native request → provider request body
 * @param unwrapResponse  provider response body → native response ({@code answers}, {@code usage})
 */
public record ApiTarget(
        String name,
        URI uri,
        Map<String, String> headers,
        UnaryOperator<JsonObject> wrapRequest,
        UnaryOperator<JsonObject> unwrapResponse
) {
    /** TypeSafe's own API: {@code POST {baseUrl}/v1/systemone}. */
    public static ApiTarget typesafe(String baseUrl, String apiKey) {
        return new ApiTarget(
                "typesafe",
                URI.create(baseUrl.replaceAll("/+$", "") + "/v1/systemone"),
                Map.of("Authorization", "Bearer " + apiKey),
                UnaryOperator.identity(),
                UnaryOperator.identity());
    }

    /**
     * Jev on Cloudflare, routed through AI Gateway:
     * {@code POST https://api.cloudflare.com/client/v4/accounts/{account}/ai/run}.
     * Without a gateway id, Cloudflare uses the account's default gateway.
     */
    public static ApiTarget cloudflare(String accountId, String apiToken, String gatewayId, String model) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiToken);
        if (gatewayId != null && !gatewayId.isBlank()) {
            headers.put("cf-aig-gateway-id", gatewayId);
        }
        headers.put("cf-aig-metadata", "{\"client\":\"jev-guard\"}");

        return new ApiTarget(
                "cloudflare",
                URI.create("https://api.cloudflare.com/client/v4/accounts/" + accountId + "/ai/run"),
                Map.copyOf(headers),
                request -> {
                    JsonObject input = request.deepCopy();
                    input.remove("model");
                    JsonObject envelope = new JsonObject();
                    envelope.addProperty("model", model);
                    envelope.add("input", input);
                    return envelope;
                },
                ApiTarget::unwrapCloudflare);
    }

    /**
     * Finds the Jev response inside whatever Cloudflare wraps it in. Seen in practice:
     * {@code {success, result: {state: "Completed", result: {answers, ...}, gatewayMetadata}}}.
     * A bare response and a single {@code result} envelope are accepted too.
     */
    static JsonObject unwrapCloudflare(JsonObject body) {
        JsonObject current = body;
        for (int depth = 0; depth < 4; depth++) {
            if (current.has("answers")) {
                return current;
            }
            JsonElement state = current.get("state");
            if (state != null && state.isJsonPrimitive() && !state.getAsString().equalsIgnoreCase("completed")) {
                throw new JevApiException(200, "Cloudflare run did not complete (state " + state.getAsString() + ")");
            }
            JsonElement result = current.get("result");
            if (result == null || !result.isJsonObject()) {
                break;
            }
            current = result.getAsJsonObject();
        }
        String errors = body.has("errors") ? body.get("errors").toString() : body.toString();
        throw new JevApiException(200, "Cloudflare returned no answers: " + errors);
    }
}
