package dev.jevguard.hytale;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import dev.jevguard.core.JevGuard;
import dev.jevguard.core.Permissions;
import java.util.UUID;

/**
 * {@code /jevguard status|reload|test <message>|simulate start [seconds]|simulate stop}.
 * Hytale commands are typed, so each subcommand is a class that rebuilds the argument array
 * core's {@code GuardCommands} expects.
 */
final class GuardCommand extends AbstractCommandCollection {
    GuardCommand(JevGuard guard) {
        super("jevguard", "jev-guard admin commands");
        requirePermission(Permissions.ADMIN);
        addSubCommand(new Simple(guard, "status", "Show mode, API health and counters", "status"));
        addSubCommand(new Simple(guard, "reload", "Reload config.yml", "reload"));
        addSubCommand(new Test(guard));
        addSubCommand(new Simulate(guard));
    }

    static void run(JevGuard guard, CommandContext ctx, String... args) {
        UUID uuid = ctx.sender().getUuid();
        Object key = uuid != null ? uuid : ctx.sender();
        guard.commands().execute(key, new HytaleAudience(ctx.sender()), args);
    }

    private static final class Simple extends CommandBase {
        private final JevGuard guard;
        private final String[] args;

        /** A subcommand without arguments that forwards fixed {@code args} to core. */
        Simple(JevGuard guard, String name, String description, String... args) {
            super(name, description);
            this.guard = guard;
            this.args = args;
            requirePermission(Permissions.ADMIN);
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            run(guard, ctx, args);
        }
    }

    private static final class Test extends CommandBase {
        private final JevGuard guard;
        private final RequiredArg<String> message;

        Test(JevGuard guard) {
            super("test", "Classify a message and show Jev's probabilities");
            this.guard = guard;
            this.message = withRequiredArg("message", "Message to classify", ArgTypes.GREEDY_STRING);
            requirePermission(Permissions.ADMIN);
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            run(guard, ctx, "test", ctx.get(message));
        }
    }

    private static final class Simulate extends AbstractCommandCollection {
        Simulate(JevGuard guard) {
            super("simulate", "Send sample chat through Jev on a loop");
            requirePermission(Permissions.ADMIN);
            addSubCommand(new Start(guard));
            addSubCommand(new Simple(guard, "stop", "Stop the simulation", "simulate", "stop"));
        }
    }

    /** {@code simulate start}, plus a usage variant for {@code simulate start <seconds>}. */
    private static final class Start extends CommandBase {
        private final JevGuard guard;

        Start(JevGuard guard) {
            super("start", "Start the simulation");
            this.guard = guard;
            requirePermission(Permissions.ADMIN);
            addUsageVariant(new StartWithInterval(guard));
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            run(guard, ctx, "simulate", "start");
        }
    }

    private static final class StartWithInterval extends CommandBase {
        private final JevGuard guard;
        private final RequiredArg<Double> seconds;

        StartWithInterval(JevGuard guard) {
            super("Start the simulation with a custom interval");
            this.guard = guard;
            this.seconds = withRequiredArg("seconds", "Seconds between messages (default 1.5)", ArgTypes.DOUBLE);
            requirePermission(Permissions.ADMIN);
        }

        @Override
        protected void executeSync(CommandContext ctx) {
            run(guard, ctx, "simulate", "start", String.valueOf(ctx.get(seconds)));
        }
    }
}
