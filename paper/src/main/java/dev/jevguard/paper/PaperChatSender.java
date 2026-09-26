package dev.jevguard.paper;

import dev.jevguard.core.ChatSender;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

record PaperChatSender(Plugin plugin, Player player) implements ChatSender {
    @Override
    public String name() {
        return player.getName();
    }

    @Override
    public UUID uuid() {
        return player.getUniqueId();
    }

    @Override
    public boolean hasPermission(String node) {
        return player.hasPermission(node);
    }

    @Override
    public void send(String template, Map<String, String> placeholders) {
        player.sendMessage(PaperText.render(template, placeholders));
    }

    @Override
    public void kick(String template, Map<String, String> placeholders) {
        Component reason = PaperText.render(template, placeholders);
        player.getScheduler().run(plugin, task -> player.kick(reason), null);
    }

    @Override
    public boolean isActive() {
        return player.isOnline();
    }
}
