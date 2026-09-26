package dev.jevguard.core;

import dev.jevguard.core.moderation.Decision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends sample chat lines through Jev on a loop and shows each verdict to the admin who
 * started it: green = approved, yellow = sent to review, red = blocked or flagged.
 * Nothing is posted to real chat and no actions run. Every line is a real API call.
 */
final class Simulator {
    static final String FILE = "simulate-messages.txt";
    private static final List<String> NAMES = List.of("Alex", "Steve", "Notch_Fan", "xXSniperXx", "BuilderBee",
            "RedstoneRita", "CreeperKid", "Mia2011", "Herobrine_", "DiamondDan");

    private record Run(Audience audience, AtomicInteger approved, AtomicInteger review, AtomicInteger blocked,
                       AtomicInteger errors, ScheduledFuture<?>[] task) {
    }

    private final JevGuard guard;
    private final ScheduledExecutorService scheduler;
    private final Map<Object, Run> runs = new ConcurrentHashMap<>();

    Simulator(JevGuard guard, ScheduledExecutorService scheduler) {
        this.guard = guard;
        this.scheduler = scheduler;
    }

    void start(Object key, Audience audience, double intervalSeconds) {
        if (runs.containsKey(key)) {
            audience.send("<gray>A simulation is already running. Use /jevguard simulate stop.</gray>");
            return;
        }
        List<String> samples = loadSamples();
        if (samples.isEmpty()) {
            audience.send("<red><file> has no messages.</red>", Map.of("file", FILE));
            return;
        }
        long periodMs = Math.max(250, (long) (intervalSeconds * 1000));
        Run run = new Run(audience, new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(),
                new ScheduledFuture<?>[1]);
        runs.put(key, run);
        run.task()[0] = scheduler.scheduleAtFixedRate(() -> tick(key, run, samples), 0, periodMs, TimeUnit.MILLISECONDS);
        audience.send("<gray>Simulating chat every <interval>s from <file> (<count> messages). Each one is a real API call. "
                + "Stop with /jevguard simulate stop.</gray>", Map.of(
                "interval", "%.1f".formatted(periodMs / 1000.0), "file", FILE, "count", String.valueOf(samples.size())));
    }

    void stop(Object key, Audience audience) {
        Run run = runs.remove(key);
        if (run == null) {
            audience.send("<gray>No simulation is running.</gray>");
            return;
        }
        run.task()[0].cancel(false);
        audience.send("<gray>Simulation stopped: </gray><green><approved> approved</green><gray>, </gray>"
                + "<yellow><review> review</yellow><gray>, </gray><red><blocked> blocked/flagged</red><gray><errors></gray>",
                Map.of("approved", run.approved().toString(), "review", run.review().toString(),
                        "blocked", run.blocked().toString(),
                        "errors", run.errors().get() > 0 ? ", " + run.errors() + " errors" : ""));
    }

    void stopAll() {
        runs.values().forEach(r -> r.task()[0].cancel(false));
        runs.clear();
    }

    private void tick(Object key, Run run, List<String> samples) {
        ChatModerator moderator = guard.moderator();
        if (!run.audience().isActive() || moderator == null) {
            Run removed = runs.remove(key);
            if (removed != null) {
                removed.task()[0].cancel(false);
            }
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String name = NAMES.get(random.nextInt(NAMES.size()));
        String text = samples.get(random.nextInt(samples.size()));

        moderator.batcher().submit(text).thenApply(moderator.policy()::decide).whenComplete((d, error) -> {
            if (error != null) {
                run.errors().incrementAndGet();
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                run.audience().send("<dark_gray>[SIM] error: <error></dark_gray>",
                        Map.of("error", String.valueOf(cause.getMessage())));
                return;
            }
            show(run, name, text, d);
        });
    }

    private static void show(Run run, String name, String text, Decision d) {
        String color;
        String label;
        String detail;
        switch (d.outcome()) {
            case ALLOW -> {
                run.approved().incrementAndGet();
                color = "green";
                label = "✔ APPROVED";
                detail = "safe %.0f%%".formatted(d.verdict().probability("safe") * 100);
            }
            case REVIEW -> {
                run.review().incrementAndGet();
                color = "yellow";
                label = "? REVIEW";
                detail = "%s %.0f%%".formatted(d.category().key(), d.probability() * 100);
            }
            default -> {
                run.blocked().incrementAndGet();
                color = "red";
                boolean cancels = d.category().actions().stream().anyMatch(a -> a.trim().equalsIgnoreCase("cancel"));
                label = cancels ? "✘ BLOCKED" : "! FLAGGED";
                detail = "%s %.0f%%".formatted(d.category().key(), d.probability() * 100);
            }
        }
        run.audience().send(("<C>[SIM] <label> </C><white><name>: </white><C><message></C><gray> (<detail>)</gray>")
                .replace("C>", color + ">"), Map.of("label", label, "name", name, "message", text, "detail", detail));
    }

    private List<String> loadSamples() {
        Path file = guard.dataDir().resolve(FILE);
        try {
            guard.saveDefault(FILE);
            return Files.readAllLines(file).stream()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .toList();
        } catch (IOException e) {
            guard.platform().logger().warning("Could not read " + FILE + ": " + e.getMessage());
            return List.of();
        }
    }
}
