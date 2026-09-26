package dev.jevguard.core;

import java.util.Map;
import java.util.logging.Logger;

/** Server-wide operations each platform provides. All methods must be safe from any thread. */
public interface Platform {
    /** Send a message to every online player with the given permission. */
    void broadcast(String permission, String template, Map<String, String> placeholders);

    /** Run a command as the server console. */
    void runConsoleCommand(String command);

    Logger logger();
}
