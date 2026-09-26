package dev.jevguard.core.jev;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Sends System One requests to an {@link ApiTarget}.
 * Retries 429, 529 and 5xx with exponential backoff, as the TypeSafe API docs recommend.
 */
public final class HttpJevClient implements JevTransport {
    private final HttpClient http;
    private final ApiTarget target;
    private final Duration requestTimeout;
    private final int maxRetries;

    public HttpJevClient(ApiTarget target, Duration requestTimeout, int maxRetries) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.target = target;
        this.requestTimeout = requestTimeout;
        this.maxRetries = maxRetries;
    }

    @Override
    public CompletableFuture<JsonObject> evaluate(JsonObject request) {
        return attempt(target.wrapRequest().apply(request).toString(), 0);
    }

    private CompletableFuture<JsonObject> attempt(String body, int attempt) {
        HttpRequest.Builder req = HttpRequest.newBuilder(target.uri())
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", "jev-guard")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        target.headers().forEach(req::header);

        return http.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() / 100 != 2) {
                        throw new JevApiException(res.statusCode(), truncate(res.body()));
                    }
                    return target.unwrapResponse().apply(JsonParser.parseString(res.body()).getAsJsonObject());
                })
                .exceptionallyCompose(err -> {
                    Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                    boolean retryable = cause instanceof JevApiException api ? api.retryable() : cause instanceof java.io.IOException;
                    if (!retryable || attempt >= maxRetries) {
                        return CompletableFuture.failedFuture(cause);
                    }
                    long delayMs = 250L << attempt;
                    Executor delayed = CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS);
                    return CompletableFuture.supplyAsync(() -> null, delayed).thenCompose(v -> attempt(body, attempt + 1));
                });
    }

    private static String truncate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
