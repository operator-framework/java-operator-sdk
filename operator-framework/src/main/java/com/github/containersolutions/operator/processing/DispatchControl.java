package com.github.containersolutions.operator.processing;

public class DispatchControl {

    private final long reprocessDelay;
    private final boolean error;

    // todo validate state on setters

    public static DispatchControl errorDuringDispatch() {
        return new DispatchControl(-1L, true);
    }

    public static DispatchControl reprocessAfter(long milliseconds) {
        return new DispatchControl(milliseconds, false);
    }

    public static DispatchControl defaultDispatch() {
        return new DispatchControl(-1L, false);
    }

    private DispatchControl(long reprocessDelay, boolean error) {
        this.reprocessDelay = reprocessDelay;
        this.error = error;
    }

    public boolean reprocessEvent() {
        return reprocessDelay > 0;
    }

    public long getReprocessDelay() {
        return reprocessDelay;
    }

    public boolean isError() {
        return error;
    }
}
