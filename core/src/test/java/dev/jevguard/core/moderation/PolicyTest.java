package dev.jevguard.core.moderation;

import static dev.jevguard.core.moderation.Fixtures.verdict;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PolicyTest {
    private final Policy policy = new Policy(Fixtures.settings(8, Duration.ZERO));

    @Test
    void safeMessageIsAllowed() {
        Decision d = policy.decide(verdict(0.97, 0.02, 0.01));
        assertEquals(Decision.Outcome.ALLOW, d.outcome());
        assertNull(d.category());
    }

    @Test
    void flagsWhenCategoryReachesItsOwnThreshold() {
        Decision d = policy.decide(verdict(0.1, 0.05, 0.85));
        assertEquals(Decision.Outcome.FLAG, d.outcome());
        assertEquals("grooming", d.category().key());
    }

    @Test
    void perCategoryThresholdsDiffer() {
        // 0.65 flags grooming (threshold 0.6) ...
        assertEquals(Decision.Outcome.FLAG, policy.decide(verdict(0.35, 0.0, 0.65)).outcome());
        // ... but is only a review for harassment (threshold 0.8, review 0.5).
        assertEquals(Decision.Outcome.REVIEW, policy.decide(verdict(0.35, 0.65, 0.0)).outcome());
    }

    @Test
    void belowReviewThresholdIsAllowed() {
        assertEquals(Decision.Outcome.ALLOW, policy.decide(verdict(0.55, 0.45, 0.0)).outcome());
    }
}
