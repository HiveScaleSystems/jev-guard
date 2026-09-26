package dev.jevguard.core.moderation;

import java.util.Map;

/**
 * Jev's answer for one chat message.
 *
 * @param choice        highest-probability option ({@code safe} or a category key)
 * @param confidence    Jev's confidence in the distribution, 0..1
 * @param probabilities probability per option; sums to 1
 */
public record Verdict(String choice, double confidence, Map<String, Double> probabilities) {
    public double probability(String option) {
        return probabilities.getOrDefault(option, 0.0);
    }
}
