package dev.jevguard.hytale;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.jevguard.core.ChatModerator.ChatResult;
import dev.jevguard.core.JevGuard;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public final class JevGuardHytalePlugin extends JavaPlugin {
    private JevGuard guard;

    public JevGuardHytalePlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        guard = new JevGuard(new HytalePlatform(getLogger()), "Hytale", "Hytale", getDataDirectory());
        guard.reload();

        // Chat handlers run on the network I/O thread, so never block: chain the verdict into
        // the event future. In hold mode the future completes when Jev answers (or times out).
        getEventRegistry().<String, PlayerChatEvent>registerAsyncGlobal(EventPriority.EARLY, PlayerChatEvent.class,
                future -> future.thenCompose(event -> {
                    if (event.isCancelled()) {
                        return CompletableFuture.completedFuture(event);
                    }
                    return guard.check(new HytaleChatSender(event.getSender()), event.getContent(), true)
                            .thenApply(result -> {
                                if (result == ChatResult.BLOCK) {
                                    event.setCancelled(true);
                                }
                                return event;
                            });
                }));

        getCommandRegistry().registerCommand(new GuardCommand(guard));
    }

    @Override
    protected void shutdown() {
        if (guard != null) {
            guard.shutdown();
        }
    }
}
