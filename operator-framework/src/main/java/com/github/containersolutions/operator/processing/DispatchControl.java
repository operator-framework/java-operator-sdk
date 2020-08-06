package com.github.containersolutions.operator.processing;

public class DispatchControl {

    public static DispatchControl reprocessAfter(long milliseconds) {
        return new DispatchControl(milliseconds);
    }

    public static DispatchControl defaultDispatch() {
        return new DispatchControl(-1L);
    }

    private DispatchControl(long reprocessDelay) {
        this.reprocessDelay = reprocessDelay;
    }

    private long reprocessDelay = -1;

    public boolean reprocessEvent() {
        return reprocessDelay > 0;
    }

    public long getReprocessDelay() {
        return reprocessDelay;
    }
}
