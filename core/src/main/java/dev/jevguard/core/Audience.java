package dev.jevguard.core;

import java.util.Map;

/**
 * Something that can receive a message: a player or the console.
 *
 * <p>Messages are templates with color tags ({@code <red>}, {@code <gray>}, …) and
 * {@code <name>} placeholders. Placeholder values are always inserted as plain text,
 * so player-written text can never inject formatting.
 */
public interface Audience {
    void send(String template, Map<String, String> placeholders);

    default void send(String template) {
        send(template, Map.of());
    }

    /** False once a player has logged out. */
    default boolean isActive() {
        return true;
    }
}
