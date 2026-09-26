package dev.jevguard.core.jev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class ApiTargetTest {
    private static JsonObject nativeRequest() {
        return JsonParser.parseString("""
                {"model": "jev-latest", "state": {"messages": ["gg"]}, "questions": {"m0": {"type": "choice"}}}
                """).getAsJsonObject();
    }

    @Test
    void typesafeSendsNativeRequest() {
        ApiTarget t = ApiTarget.typesafe("https://api.typesafe.ai/", "ts_key");
        assertEquals("https://api.typesafe.ai/v1/systemone", t.uri().toString());
        assertEquals("Bearer ts_key", t.headers().get("Authorization"));
        JsonObject req = nativeRequest();
        assertSame(req, t.wrapRequest().apply(req));
    }

    @Test
    void cloudflareWrapsInRunEnvelopeWithGateway() {
        ApiTarget t = ApiTarget.cloudflare("acc123", "cf_token", "mc-gateway", "typesafe/jev");
        assertEquals("https://api.cloudflare.com/client/v4/accounts/acc123/ai/run", t.uri().toString());
        assertEquals("mc-gateway", t.headers().get("cf-aig-gateway-id"));

        JsonObject body = t.wrapRequest().apply(nativeRequest());
        assertEquals("typesafe/jev", body.get("model").getAsString());
        JsonObject input = body.getAsJsonObject("input");
        assertFalse(input.has("model"));
        assertEquals("gg", input.getAsJsonObject("state").getAsJsonArray("messages").get(0).getAsString());
    }

    @Test
    void cloudflareWithoutGatewayUsesDefault() {
        ApiTarget t = ApiTarget.cloudflare("acc123", "cf_token", "", "typesafe/jev");
        assertFalse(t.headers().containsKey("cf-aig-gateway-id"));
    }

    @Test
    void cloudflareUnwrapsBareAndEnvelopedResponses() {
        JsonObject bare = JsonParser.parseString("{\"answers\": {}}").getAsJsonObject();
        JsonObject enveloped = JsonParser.parseString(
                "{\"success\": true, \"errors\": [], \"result\": {\"answers\": {\"m0\": {}}}}").getAsJsonObject();

        assertSame(bare, ApiTarget.unwrapCloudflare(bare));
        assertEquals(1, ApiTarget.unwrapCloudflare(enveloped).getAsJsonObject("answers").size());
        assertThrows(JevApiException.class, () -> ApiTarget.unwrapCloudflare(
                JsonParser.parseString("{\"success\": false, \"errors\": [{\"code\": 10000}]}").getAsJsonObject()));
    }

    @Test
    void cloudflareUnwrapsUnifiedBillingRunEnvelope() {
        // Real response from /ai/run with typesafe/jev through AI Gateway (Unified Billing), 2026-09-24.
        JsonObject body = JsonParser.parseString("""
                {"result":{"state":"Completed","result":{"model":"jev-1.13.0","answers":{"m0":{"type":"choice",
                "choice":"safe","probabilities":{"safe":1,"harassment":0},"confidence":0.99}},
                "usage":{"input_tokens":400,"output_tokens":67}},"gatewayMetadata":{"keySource":"Unified"}},
                "success":true,"errors":[],"messages":[]}
                """).getAsJsonObject();
        JsonObject unwrapped = ApiTarget.unwrapCloudflare(body);
        assertEquals("safe", unwrapped.getAsJsonObject("answers").getAsJsonObject("m0").get("choice").getAsString());
    }

    @Test
    void cloudflareRejectsUnfinishedRun() {
        JsonObject body = JsonParser.parseString("{\"result\":{\"state\":\"Running\"},\"success\":true}")
                .getAsJsonObject();
        assertThrows(JevApiException.class, () -> ApiTarget.unwrapCloudflare(body));
    }
}
