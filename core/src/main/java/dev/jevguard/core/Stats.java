package dev.jevguard.core;

import java.util.concurrent.atomic.AtomicLong;

public final class Stats {
    public final AtomicLong checked = new AtomicLong();
    public final AtomicLong flagged = new AtomicLong();
    public final AtomicLong reviewed = new AtomicLong();
    public final AtomicLong errors = new AtomicLong();
    public final AtomicLong timeouts = new AtomicLong();
}
