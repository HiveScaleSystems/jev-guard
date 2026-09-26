package dev.jevguard.hytale;

import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.jevguard.core.Audience;
import java.util.Map;

record HytaleAudience(CommandSender sender) implements Audience {
    @Override
    public void send(String template, Map<String, String> placeholders) {
        sender.sendMessage(HytaleText.render(template, placeholders));
    }

    @Override
    public boolean isActive() {
        return !(sender instanceof PlayerRef p) || Universe.get().getPlayer(p.getUuid()) != null;
    }
}
