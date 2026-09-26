package dev.jevguard.core;

import dev.jevguard.core.jev.ApiTarget;
import dev.jevguard.core.moderation.Category;
import dev.jevguard.core.moderation.ModerationSettings;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import org.yaml.snakeyaml.Yaml;

public record GuardConfig(
        ApiTarget target,
        Duration requestTimeout,
        int maxRetries,
        Mode mode,
        boolean failOpen,
        Duration holdTimeout,
        int minMessageLength,
        boolean logMessageContent,
        int breakerFailures,
        Duration breakerCooldown,
        ModerationSettings moderation,
        Messages messages
) {
    public record Messages(String warn, String kick, String unavailable, String staffBlocked, String staffFlag,
                           String staffReview) {
    }

    public static GuardConfig load(Path file, String game) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return load(new Yaml().<Map<String, Object>>load(reader), System::getenv, game);
        }
    }

    /**
     * @param env  environment lookup; secrets in the environment win over config values
     * @param game game name used in Jev's instructions, e.g. "Minecraft"
     */
    public static GuardConfig load(Map<String, Object> yaml, Function<String, String> env, String game) {
        ConfigView c = new ConfigView(yaml);
        ApiTarget target = loadTarget(c, env);

        List<Category> categories = new ArrayList<>();
        for (Map.Entry<?, ?> e : c.section("categories").entrySet()) {
            String key = String.valueOf(e.getKey());
            ConfigView s = new ConfigView(e.getValue() instanceof Map<?, ?> m ? m : Map.of());
            if (!s.bool("enabled", true)) {
                continue;
            }
            if (key.equals(ModerationSettings.SAFE)) {
                throw new IllegalArgumentException("'safe' is reserved and cannot be a category key");
            }
            categories.add(new Category(key, s.string("description", key), s.number("threshold", 0.8),
                    s.strings("actions")));
        }
        if (categories.isEmpty()) {
            throw new IllegalArgumentException("config.yml defines no enabled categories");
        }

        ModerationSettings moderation = new ModerationSettings(
                game,
                c.string("api.typesafe.model", "jev-latest"),
                c.string("safe-description", "Ordinary chat."),
                List.copyOf(categories),
                c.number("review-threshold", 0.5),
                Math.max(1, c.integer("batching.max-size", 8)),
                Duration.ofMillis(Math.max(0, c.longValue("batching.max-wait-ms", 150))));

        String mode = c.string("mode", "shadow").toUpperCase(Locale.ROOT);
        try {
            Mode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("mode must be shadow, deliver or hold, not '" + mode.toLowerCase() + "'");
        }

        return new GuardConfig(
                target,
                Duration.ofMillis(c.longValue("api.timeout-ms", 4000)),
                c.integer("api.max-retries", 2),
                Mode.valueOf(mode),
                !c.string("on-error", "allow").equalsIgnoreCase("block"),
                Duration.ofMillis(c.longValue("hold.timeout-ms", 1500)),
                c.integer("min-message-length", 2),
                c.bool("privacy.log-message-content", true),
                c.integer("circuit-breaker.failures", 5),
                Duration.ofSeconds(c.longValue("circuit-breaker.cooldown-seconds", 30)),
                moderation,
                new Messages(
                        c.string("messages.warn", ""),
                        c.string("messages.kick", ""),
                        c.string("messages.unavailable", ""),
                        c.string("messages.staff-blocked", ""),
                        c.string("messages.staff-flag", ""),
                        c.string("messages.staff-review", "")));
    }

    private static ApiTarget loadTarget(ConfigView c, Function<String, String> env) {
        String provider = c.string("api.provider", "typesafe").toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "typesafe" -> {
                String key = secret(env, "TYPESAFE_API_KEY", c.string("api.typesafe.key", ""));
                if (key.isBlank()) {
                    throw new IllegalArgumentException("No TypeSafe API key. Set TYPESAFE_API_KEY or api.typesafe.key.");
                }
                yield ApiTarget.typesafe(c.string("api.typesafe.base-url", "https://api.typesafe.ai"), key);
            }
            case "cloudflare" -> {
                String account = secret(env, "CLOUDFLARE_ACCOUNT_ID", c.string("api.cloudflare.account-id", ""));
                String token = secret(env, "CLOUDFLARE_API_TOKEN", c.string("api.cloudflare.api-token", ""));
                if (account.isBlank() || token.isBlank()) {
                    throw new IllegalArgumentException("Cloudflare needs an account id and API token. Set "
                            + "CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN, or api.cloudflare.* in config.yml.");
                }
                yield ApiTarget.cloudflare(account, token, c.string("api.cloudflare.gateway-id", ""),
                        c.string("api.cloudflare.model", "typesafe/jev"));
            }
            default -> throw new IllegalArgumentException("api.provider must be 'typesafe' or 'cloudflare', not '"
                    + provider + "'");
        };
    }

    private static String secret(Function<String, String> env, String name, String fallback) {
        String value = env.apply(name);
        return value != null && !value.isBlank() ? value : fallback;
    }
}
