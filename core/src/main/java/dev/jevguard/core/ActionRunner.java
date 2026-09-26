package dev.jevguard.core;

import dev.jevguard.core.moderation.Decision;
import java.util.Locale;
import java.util.Map;

/** Runs the actions configured for a category, through the {@link Platform}. */
final class ActionRunner {
    private final Platform platform;
    private final GuardConfig config;

    ActionRunner(Platform platform, GuardConfig config) {
        this.platform = platform;
        this.config = config;
    }

    void logDecision(ChatSender player, String message, Decision decision, boolean blocked) {
        if (decision.outcome() == Decision.Outcome.ALLOW) {
            return;
        }
        String content = config.logMessageContent() ? " message=\"" + message + "\"" : "";
        platform.logger().info("[%s] player=%s category=%s p=%.2f confidence=%.2f mode=%s%s".formatted(
                blocked ? "BLOCKED" : decision.outcome(), player.name(), decision.category().key(),
                decision.probability(), decision.verdict().confidence(),
                config.mode().name().toLowerCase(Locale.ROOT), content));
    }

    /**
     * @param blocked true if the message was cancelled; staff with {@link Permissions#NOTIFY}
     *                are then always told, with {@code messages.staff-blocked}
     */
    void run(ChatSender player, String message, Decision decision, boolean blocked) {
        Map<String, String> ph = placeholders(player, message, decision);
        if (decision.outcome() == Decision.Outcome.REVIEW) {
            notifyStaff(config.messages().staffReview(), ph);
            return;
        }
        if (blocked) {
            notifyStaff(config.messages().staffBlocked(), ph);
        }
        for (String action : decision.category().actions()) {
            String a = action.trim();
            if (a.equalsIgnoreCase("notify-staff")) {
                if (!blocked) {
                    notifyStaff(config.messages().staffFlag(), ph);
                }
            } else if (a.equalsIgnoreCase("warn")) {
                if (!config.messages().warn().isBlank()) {
                    player.send(config.messages().warn(), ph);
                }
            } else if (a.equalsIgnoreCase("kick")) {
                player.kick(config.messages().kick(), ph);
            } else if (a.regionMatches(true, 0, "command:", 0, 8)) {
                // {message} is deliberately not offered: player text must never reach a console command.
                platform.runConsoleCommand(a.substring(8).trim()
                        .replace("{player}", player.name())
                        .replace("{uuid}", player.uuid().toString())
                        .replace("{category}", decision.category().key()));
            } else if (!a.equalsIgnoreCase("cancel")) {
                platform.logger().warning("Unknown action '" + a + "' in category " + decision.category().key());
            }
        }
    }

    void tellUnavailable(ChatSender player) {
        if (!config.messages().unavailable().isBlank()) {
            player.send(config.messages().unavailable());
        }
    }

    private void notifyStaff(String template, Map<String, String> placeholders) {
        if (!template.isBlank()) {
            platform.broadcast(Permissions.NOTIFY, template, placeholders);
        }
    }

    static Map<String, String> placeholders(ChatSender player, String message, Decision decision) {
        return Map.of(
                "player", player.name(),
                "message", message,
                "category", decision.category().key(),
                "probability", "%.0f%%".formatted(decision.probability() * 100));
    }
}
