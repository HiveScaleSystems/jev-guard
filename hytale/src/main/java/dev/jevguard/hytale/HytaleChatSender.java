package dev.jevguard.hytale;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.jevguard.core.ChatSender;
import java.util.Map;
import java.util.UUID;

record HytaleChatSender(PlayerRef player) implements ChatSender {
    @Override
    public String name() {
        return player.getUsername();
    }

    @Override
    public UUID uuid() {
        return player.getUuid();
    }

    @Override
    public boolean hasPermission(String node) {
        return player.hasPermission(node);
    }

    @Override
    public void send(String template, Map<String, String> placeholders) {
        player.sendMessage(HytaleText.render(template, placeholders));
    }

    @Override
    public void kick(String template, Map<String, String> placeholders) {
        player.getPacketHandler().disconnect(HytaleText.render(template, placeholders));
    }

    @Override
    public boolean isActive() {
        return Universe.get().getPlayer(player.getUuid()) != null;
    }
}
