package dev.jevguard.core;

import dev.jevguard.core.jev.HttpJevClient;
import dev.jevguard.core.moderation.CircuitBreaker;
import dev.jevguard.core.moderation.ModerationBatcher;
import dev.jevguard.core.moderation.Policy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

/**
 * The platform-independent plugin. A platform plugin creates one, calls {@link #reload()} on
 * enable and {@link #shutdown()} on disable, forwards chat to {@link #check} and the
 * {@code /jevguard} command to {@link #commands()}.
 */
public final class JevGuard {
    /** Everything built from one config load, swapped as a unit on reload. */
    record Runtime(GuardConfig config, ModerationBatcher batcher, CircuitBreaker breaker, ChatModerator moderator) {
    }

    private final Platform platform;
    private final String platformName;
    private final String game;
    private final Path dataDir;
    private final Stats stats = new Stats();
    private final ScheduledExecutorService scheduler;
    private final Simulator simulator;
    private final GuardCommands commands;
    private volatile Runtime runtime;

    /**
     * @param platformName shown in {@code /jevguard status}, e.g. "Paper"
     * @param game         game name used in Jev's instructions, e.g. "Minecraft"
     * @param dataDir      folder for config.yml and simulate-messages.txt
     */
    public JevGuard(Platform platform, String platformName, String game, Path dataDir) {
        this.platform = platform;
        this.platformName = platformName;
        this.game = game;
        this.dataDir = dataDir;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "jev-guard-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.simulator = new Simulator(this, scheduler);
        this.commands = new GuardCommands(this);
    }

    /** Loads config.yml (writing the default first if missing). @return true if moderation is running */
    public synchronized boolean reload() {
        stopRuntime();
        GuardConfig config;
        try {
            saveDefault("config.yml");
            config = GuardConfig.load(dataDir.resolve("config.yml"), game);
        } catch (IllegalArgumentException | IOException e) {
            platform.logger().log(Level.SEVERE, "Invalid config.yml: " + e.getMessage());
            platform.logger().severe("Chat moderation is OFF until the config is fixed and /jevguard reload is run.");
            return false;
        } catch (RuntimeException e) {
            // SnakeYAML syntax errors
            platform.logger().log(Level.SEVERE, "Could not parse config.yml: " + e.getMessage());
            return false;
        }

        HttpJevClient client = new HttpJevClient(config.target(), config.requestTimeout(), config.maxRetries());
        CircuitBreaker breaker = new CircuitBreaker(config.breakerFailures(), config.breakerCooldown(),
                Clock.systemUTC());
        ModerationBatcher batcher = new ModerationBatcher(client, config.moderation(), breaker);
        ChatModerator moderator = new ChatModerator(config, batcher, new Policy(config.moderation()), platform, stats);
        runtime = new Runtime(config, batcher, breaker, moderator);

        platform.logger().info("Moderating chat via %s in %s mode (%d categories).".formatted(
                config.target().name(), config.mode().name().toLowerCase(Locale.ROOT),
                config.moderation().categories().size()));
        return true;
    }

    /** See {@link ChatModerator#check}. Allows everything while moderation is off. */
    public CompletableFuture<ChatModerator.ChatResult> check(ChatSender sender, String text, boolean canHold) {
        ChatModerator moderator = moderator();
        return moderator == null
                ? CompletableFuture.completedFuture(ChatModerator.ChatResult.ALLOW)
                : moderator.check(sender, text, canHold);
    }

    public GuardCommands commands() {
        return commands;
    }

    public void shutdown() {
        simulator.stopAll();
        stopRuntime();
        scheduler.shutdownNow();
    }

    /** Copies a bundled default file into the data folder if it is not there yet. */
    public void saveDefault(String name) throws IOException {
        Path target = dataDir.resolve(name);
        if (Files.exists(target)) {
            return;
        }
        Files.createDirectories(dataDir);
        try (InputStream in = JevGuard.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IOException("Bundled " + name + " is missing from the jar");
            }
            Files.copy(in, target);
        }
    }

    private void stopRuntime() {
        Runtime current = runtime;
        runtime = null;
        if (current != null) {
            current.batcher().close();
        }
    }

    ChatModerator moderator() {
        Runtime rt = runtime;
        return rt == null ? null : rt.moderator();
    }

    Runtime runtime() {
        return runtime;
    }

    Simulator simulator() {
        return simulator;
    }

    Stats stats() {
        return stats;
    }

    Platform platform() {
        return platform;
    }

    String platformName() {
        return platformName;
    }

    Path dataDir() {
        return dataDir;
    }
}
