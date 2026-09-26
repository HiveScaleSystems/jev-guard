package dev.jevguard.paper;

import dev.jevguard.core.JevGuard;
import org.bukkit.plugin.java.JavaPlugin;

public final class JevGuardPaperPlugin extends JavaPlugin {
    private JevGuard guard;

    @Override
    public void onEnable() {
        guard = new JevGuard(new PaperPlatform(this), "Paper", "Minecraft", getDataFolder().toPath());
        guard.reload();
        getServer().getPluginManager().registerEvents(new ChatListener(this, guard), this);
        registerCommand("jevguard", "jev-guard admin commands", new GuardCommand(guard));
    }

    @Override
    public void onDisable() {
        if (guard != null) {
            guard.shutdown();
        }
    }
}
