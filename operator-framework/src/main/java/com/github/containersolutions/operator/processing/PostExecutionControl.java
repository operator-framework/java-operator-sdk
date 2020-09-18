package com.github.containersolutions.operator.processing;

public final class PostExecutionControl {

    private final long reprocessDelay;
    private final boolean error;
    private final boolean onlyFinalizerHandled;

    // todo validate state on setters

    public static PostExecutionControl onlyFinalizerAdded() {
        return new PostExecutionControl(-1L, false, true);
    }

    public static PostExecutionControl errorDuringDispatch() {
        return new PostExecutionControl(-1L, true, false);
    }

    public static PostExecutionControl reprocessAfter(long milliseconds) {
        return new PostExecutionControl(milliseconds, false, false);
    }

    public static PostExecutionControl defaultDispatch() {
        return new PostExecutionControl(-1L, false, false);
    }

    private PostExecutionControl(long reprocessDelay, boolean error, boolean onlyFinalizerHandled) {
        this.reprocessDelay = reprocessDelay;
        this.error = error;
        this.onlyFinalizerHandled = onlyFinalizerHandled;
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

    public boolean isOnlyFinalizerHandled() {
        return onlyFinalizerHandled;
    }
}
