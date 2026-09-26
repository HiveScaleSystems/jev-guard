package dev.jevguard.core.moderation;

/** Turns a Jev verdict into a decision. Thresholds live here, in code, not in the model. */
public final class Policy {
    private final ModerationSettings settings;

    public Policy(ModerationSettings settings) {
        this.settings = settings;
    }

    public Decision decide(Verdict verdict) {
        Category top = null;
        double topProbability = -1;
        for (Category category : settings.categories()) {
            double p = verdict.probability(category.key());
            if (p > topProbability) {
                top = category;
                topProbability = p;
            }
        }
        if (top == null) {
            return new Decision(Decision.Outcome.ALLOW, null, verdict);
        }
        if (topProbability >= top.threshold()) {
            return new Decision(Decision.Outcome.FLAG, top, verdict);
        }
        if (topProbability >= settings.reviewThreshold()) {
            return new Decision(Decision.Outcome.REVIEW, top, verdict);
        }
        return new Decision(Decision.Outcome.ALLOW, null, verdict);
    }
}
