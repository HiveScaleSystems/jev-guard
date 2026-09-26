package dev.jevguard.core;

import dev.jevguard.core.moderation.Decision;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /jevguard <reload|status|test <message>|simulate <start [seconds]|stop>>}.
 * Platforms register the command, check {@link Permissions#ADMIN}, and pass arguments here.
 */
public final class GuardCommands {
    private static final String USAGE = "Usage: /jevguard <reload|status|test <message>|simulate <start [seconds]|stop>>";

    private final JevGuard guard;

    GuardCommands(JevGuard guard) {
        this.guard = guard;
    }

    /**
     * @param senderKey stable identity of the sender (e.g. the sender object), used to track simulations
     */
    public void execute(Object senderKey, Audience sender, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> sender.send(guard.reload()
                    ? "<green>jev-guard reloaded.</green>"
                    : "<red>Reload failed; moderation is OFF. See console.</red>");
            case "status" -> status(sender);
            case "test" -> test(sender, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            case "simulate" -> simulate(senderKey, sender, args);
            default -> usage(sender, USAGE);
        }
    }

    public List<String> suggest(String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return List.of("reload", "status", "test", "simulate").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("simulate")) {
            return List.of("start", "stop").stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    private void simulate(Object key, Audience sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "start" -> {
                if (guard.moderator() == null) {
                    sender.send("<red>Moderation is OFF (check console).</red>");
                    return;
                }
                double seconds = 1.5;
                if (args.length > 2) {
                    try {
                        seconds = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        sender.send("<red>Interval must be a number of seconds.</red>");
                        return;
                    }
                }
                guard.simulator().start(key, sender, seconds);
            }
            case "stop" -> guard.simulator().stop(key, sender);
            default -> usage(sender, "Usage: /jevguard simulate <start [seconds]|stop>");
        }
    }

    private void status(Audience sender) {
        JevGuard.Runtime rt = guard.runtime();
        if (rt == null) {
            sender.send("<red>Moderation is OFF (check console).</red>");
            return;
        }
        Stats s = guard.stats();
        sender.send("<gray>jev-guard on <platform>: mode=<mode> provider=<provider> api=<api>\n"
                + "checked=<checked> flagged=<flagged> review=<review> errors=<errors> timeouts=<timeouts></gray>", Map.of(
                "platform", guard.platformName(),
                "mode", rt.config().mode().name().toLowerCase(Locale.ROOT),
                "provider", rt.config().target().name(),
                "api", rt.breaker().isOpen() ? "PAUSED (circuit open)" : "ok",
                "checked", s.checked.toString(), "flagged", s.flagged.toString(), "review", s.reviewed.toString(),
                "errors", s.errors.toString(), "timeouts", s.timeouts.toString()));
    }

    private void test(Audience sender, String text) {
        ChatModerator moderator = guard.moderator();
        if (moderator == null) {
            sender.send("<red>Moderation is OFF (check console).</red>");
            return;
        }
        if (text.isBlank()) {
            usage(sender, "Usage: /jevguard test <message>");
            return;
        }
        moderator.batcher().submit(text).whenComplete((verdict, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                sender.send("<red>Request failed: <error></red>", Map.of("error", String.valueOf(cause.getMessage())));
                return;
            }
            Decision d = moderator.policy().decide(verdict);
            String probabilities = verdict.probabilities().entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                    .map(e -> "%s %.0f%%".formatted(e.getKey(), e.getValue() * 100))
                    .collect(Collectors.joining(", "));
            String color = switch (d.outcome()) {
                case ALLOW -> "green";
                case REVIEW -> "yellow";
                case FLAG -> "red";
            };
            sender.send("<" + color + "><outcome> (confidence <confidence>): <probabilities></" + color + ">", Map.of(
                    "outcome", d.outcome().name(),
                    "confidence", "%.2f".formatted(verdict.confidence()),
                    "probabilities", probabilities));
        });
    }

    /** Usage text contains literal {@code <...>}, so it goes in as a placeholder, not as markup. */
    private static void usage(Audience sender, String text) {
        sender.send("<gray><usage></gray>", Map.of("usage", text));
    }
}
