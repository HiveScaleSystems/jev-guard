package dev.jevguard.core.moderation;

import java.time.Duration;
import java.util.List;
import java.util.Map;

final class Fixtures {
    static final Category HARASSMENT = new Category("harassment", "Insults at a player", 0.8, List.of("cancel", "warn"));
    static final Category GROOMING = new Category("grooming", "Asks for personal details", 0.6, List.of("notify-staff"));

    static ModerationSettings settings(int batchSize, Duration maxWait) {
        return new ModerationSettings("Minecraft", "jev-latest", "Ordinary chat.", List.of(HARASSMENT, GROOMING), 0.5,
                batchSize, maxWait);
    }

    static Verdict verdict(double safe, double harassment, double grooming) {
        String choice = safe >= harassment && safe >= grooming ? "safe" : harassment >= grooming ? "harassment" : "grooming";
        return new Verdict(choice, 0.9, Map.of("safe", safe, "harassment", harassment, "grooming", grooming));
    }

    private Fixtures() {
    }
}
