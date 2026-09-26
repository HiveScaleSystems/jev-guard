package dev.jevguard.core.moderation;

import java.time.Duration;
import java.util.List;

/**
 * @param game            game name used in Jev's instructions, e.g. "Minecraft"
 * @param model           Jev model name, e.g. {@code jev-latest}
 * @param safeDescription rubric text for the "safe" option
 * @param categories      harmful categories, in config order
 * @param reviewThreshold probability at or above which a message is sent to staff for review
 * @param batchSize       max messages per API request
 * @param maxBatchWait    max time a message waits for its batch to fill
 */
public record ModerationSettings(
        String game,
        String model,
        String safeDescription,
        List<Category> categories,
        double reviewThreshold,
        int batchSize,
        Duration maxBatchWait
) {
    public static final String SAFE = "safe";
}
