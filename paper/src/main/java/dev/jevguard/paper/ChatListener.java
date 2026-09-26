package dev.jevguard.paper;

import dev.jevguard.core.ChatModerator.ChatResult;
import dev.jevguard.core.JevGuard;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

final class ChatListener implements Listener {
    private final Plugin plugin;
    private final JevGuard guard;

    ChatListener(Plugin plugin, JevGuard guard) {
        this.plugin = plugin;
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        // Waiting for the verdict is only safe on the async chat thread, never on a main/region thread.
        boolean canHold = event.isAsynchronous();
        ChatResult result = guard.check(new PaperChatSender(plugin, event.getPlayer()), text, canHold).join();
        if (result == ChatResult.BLOCK) {
            event.setCancelled(true);
        }
    }
}
