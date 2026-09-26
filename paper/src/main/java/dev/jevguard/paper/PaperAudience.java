package dev.jevguard.paper;

import dev.jevguard.core.Audience;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

record PaperAudience(CommandSender sender) implements Audience {
    @Override
    public void send(String template, Map<String, String> placeholders) {
        sender.sendMessage(PaperText.render(template, placeholders));
    }

    @Override
    public boolean isActive() {
        return !(sender instanceof Player p) || p.isOnline();
    }
}
