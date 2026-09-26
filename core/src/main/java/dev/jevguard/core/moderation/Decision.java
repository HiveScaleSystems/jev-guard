package dev.jevguard.core.moderation;

/**
 * What to do with one message.
 *
 * @param outcome  ALLOW, REVIEW (notify staff only) or FLAG (run the category's actions)
 * @param category top harmful category, or null when the message is allowed
 * @param verdict  the underlying Jev answer
 */
public record Decision(Outcome outcome, Category category, Verdict verdict) {
    public enum Outcome { ALLOW, REVIEW, FLAG }

    public double probability() {
        return category == null ? 0.0 : verdict.probability(category.key());
    }
}
