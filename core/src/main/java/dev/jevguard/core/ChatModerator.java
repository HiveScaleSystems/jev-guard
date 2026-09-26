package dev.jevguard.core;

import dev.jevguard.core.moderation.Decision;
import dev.jevguard.core.moderation.ModerationBatcher;
import dev.jevguard.core.moderation.Policy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** The platform-independent chat flow: filter, ask Jev, decide, act. */
public final class ChatModerator {
    public enum ChatResult { ALLOW, BLOCK }

    private final GuardConfig config;
    private final ModerationBatcher batcher;
    private final Policy policy;
    private final ActionRunner actions;
    private final Stats stats;
    private final Platform platform;

    ChatModerator(GuardConfig config, ModerationBatcher batcher, Policy policy, Platform platform, Stats stats) {
        this.config = config;
        this.batcher = batcher;
        this.policy = policy;
        this.actions = new ActionRunner(platform, config);
        this.stats = stats;
        this.platform = platform;
    }

    /**
     * Checks one chat message.
     *
     * <p>In {@code hold} mode (and when {@code canHold} is true) the future completes with the
     * verdict, or with the {@code on-error} fallback after {@code hold.timeout-ms}; the platform
     * cancels the message on {@link ChatResult#BLOCK}. In every other case the future is already
     * complete with {@link ChatResult#ALLOW} and actions run when the verdict arrives.
     *
     * @param canHold false when the platform cannot wait for the result (e.g. a synchronous event)
     */
    public CompletableFuture<ChatResult> check(ChatSender sender, String text, boolean canHold) {
        String trimmed = text.trim();
        if (sender.hasPermission(Permissions.BYPASS) || trimmed.length() < config.minMessageLength()) {
            return CompletableFuture.completedFuture(ChatResult.ALLOW);
        }
        stats.checked.incrementAndGet();
        CompletableFuture<Decision> decision = batcher.submit(trimmed).thenApply(policy::decide);

        if (config.mode() != Mode.HOLD || !canHold) {
            decision.whenComplete((d, error) -> {
                if (error != null) {
                    onError(error);
                } else {
                    handle(sender, trimmed, d, false);
                }
            });
            return CompletableFuture.completedFuture(ChatResult.ALLOW);
        }

        // Whoever claims first (verdict or timeout) decides; the verdict may still arrive late.
        CompletableFuture<ChatResult> result = new CompletableFuture<>();
        AtomicBoolean claimed = new AtomicBoolean();
        decision.whenComplete((d, error) -> {
            boolean inTime = claimed.compareAndSet(false, true);
            if (error != null) {
                onError(error);
                if (inTime) {
                    fallback(sender, result);
                }
                return;
            }
            // After a timeout the message is already out; still run the other actions.
            boolean block = handle(sender, trimmed, d, inTime);
            result.complete(block ? ChatResult.BLOCK : ChatResult.ALLOW);
        });
        CompletableFuture.delayedExecutor(config.holdTimeout().toMillis(), TimeUnit.MILLISECONDS).execute(() -> {
            if (claimed.compareAndSet(false, true)) {
                stats.timeouts.incrementAndGet();
                fallback(sender, result);
            }
        });
        return result;
    }

    Policy policy() {
        return policy;
    }

    ModerationBatcher batcher() {
        return batcher;
    }

    private void fallback(ChatSender sender, CompletableFuture<ChatResult> result) {
        ChatResult r = config.failOpen() ? ChatResult.ALLOW : ChatResult.BLOCK;
        if (result.complete(r) && r == ChatResult.BLOCK) {
            actions.tellUnavailable(sender);
        }
    }

    /** @return true if the message should be cancelled */
    private boolean handle(ChatSender sender, String text, Decision d, boolean canCancel) {
        switch (d.outcome()) {
            case ALLOW -> {
                return false;
            }
            case FLAG -> stats.flagged.incrementAndGet();
            case REVIEW -> stats.reviewed.incrementAndGet();
        }
        boolean block = config.mode() != Mode.SHADOW && canCancel && d.outcome() == Decision.Outcome.FLAG
                && d.category().actions().stream().anyMatch(a -> a.trim().equalsIgnoreCase("cancel"));
        actions.logDecision(sender, text, d, block);
        if (config.mode() == Mode.SHADOW) {
            return false;
        }
        actions.run(sender, text, d, block);
        return block;
    }

    private void onError(Throwable error) {
        long n = stats.errors.incrementAndGet();
        // Avoid flooding the console during an outage.
        if (n <= 5 || n % 100 == 0) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            platform.logger().log(Level.WARNING, "Moderation request failed (" + n + " total): " + cause.getMessage());
        }
    }
}
