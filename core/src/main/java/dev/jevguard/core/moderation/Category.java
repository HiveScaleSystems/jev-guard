package dev.jevguard.core.moderation;

import java.util.List;

/**
 * One harmful category players' messages are checked against.
 *
 * @param key         option key sent to Jev and used in logs
 * @param description rubric text Jev reads for this option
 * @param threshold   probability at or above which the message is flagged
 * @param actions     actions run when flagged (see {@code config.yml})
 */
public record Category(String key, String description, double threshold, List<String> actions) {
}
