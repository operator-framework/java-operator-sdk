package com.github.containersolutions.operator.api;

import java.util.concurrent.TimeUnit;

public class ReprocessControl {

    private long reprocessDelay = -1;

    public long getReprocessDelay() {
        return reprocessDelay;
    }
    public boolean isForReprocess() {
        return reprocessDelay > 0;
    }

    public ReprocessControl reprocessAfter(long milliseconds) {
        this.reprocessDelay = milliseconds;
        return this;
    }

    public ReprocessControl reprocessAfter(long delay, TimeUnit timeUnit) {
        reprocessAfter(timeUnit.toMillis(delay));
        return this;
    }
}
