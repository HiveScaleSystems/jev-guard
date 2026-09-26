package dev.jevguard.core;

import java.util.Map;
import java.util.UUID;

/** A player who sent a chat message. Implemented by each platform. */
public interface ChatSender extends Audience {
    String name();

    UUID uuid();

    boolean hasPermission(String node);

    /** Disconnect the player. Implementations must be safe to call from any thread. */
    void kick(String template, Map<String, String> placeholders);
}
