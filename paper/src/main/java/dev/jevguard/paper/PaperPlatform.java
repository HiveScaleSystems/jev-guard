package dev.jevguard.paper;

import dev.jevguard.core.Platform;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Folia-safe: console commands run on the global region scheduler. */
final class PaperPlatform implements Platform {
    private final Plugin plugin;

    PaperPlatform(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void broadcast(String permission, String template, Map<String, String> placeholders) {
        Component line = PaperText.render(template, placeholders);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(permission)) {
                online.sendMessage(line);
            }
        }
    }

    @Override
    public void runConsoleCommand(String command) {
        Bukkit.getGlobalRegionScheduler().execute(plugin,
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    @Override
    public Logger logger() {
        return plugin.getLogger();
    }
}
