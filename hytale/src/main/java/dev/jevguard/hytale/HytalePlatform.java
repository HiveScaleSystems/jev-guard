package dev.jevguard.hytale;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.jevguard.core.Platform;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Hytale sends messages over thread-safe Netty channels, so everything here is safe off-thread. */
final class HytalePlatform implements Platform {
    private final Logger logger;

    HytalePlatform(HytaleLogger hytaleLogger) {
        this.logger = bridge(hytaleLogger);
    }

    @Override
    public void broadcast(String permission, String template, Map<String, String> placeholders) {
        Message line = HytaleText.render(template, placeholders);
        for (PlayerRef player : Universe.get().getPlayers()) {
            if (player.hasPermission(permission)) {
                player.sendMessage(line);
            }
        }
    }

    @Override
    public void runConsoleCommand(String command) {
        CommandManager.get().handleCommand(ConsoleSender.INSTANCE, command.startsWith("/") ? command.substring(1) : command);
    }

    @Override
    public Logger logger() {
        return logger;
    }

    /** Core logs through java.util.logging; forward those records to the plugin's Flogger-based logger. */
    private static Logger bridge(HytaleLogger target) {
        Logger jul = Logger.getAnonymousLogger();
        jul.setUseParentHandlers(false);
        jul.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                target.at(record.getLevel()).log("%s", record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return jul;
    }
}
