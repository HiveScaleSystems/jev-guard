package dev.jevguard.core;

public enum Mode {
    /** Only log verdicts. Nothing is blocked or punished. Start here to tune thresholds. */
    SHADOW,
    /** Messages are delivered at once; actions run when the verdict arrives. */
    DELIVER,
    /** Messages wait for the verdict (up to a timeout) and flagged ones can be cancelled. */
    HOLD
}
