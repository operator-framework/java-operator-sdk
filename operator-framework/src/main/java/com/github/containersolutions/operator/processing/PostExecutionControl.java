package com.github.containersolutions.operator.processing;

public final class PostExecutionControl {

    private final long reprocessDelay;
    private final RuntimeException exception;
    private final boolean onlyFinalizerHandled;

    // todo nicer class hierarchy?
    // todo validate state on setters

    public static PostExecutionControl onlyFinalizerAdded() {
        return new PostExecutionControl(-1L, null, true);
    }

    public static PostExecutionControl errorDuringDispatch(RuntimeException exception) {
        return new PostExecutionControl(-1L, exception, false);
    }

    public static PostExecutionControl reprocessAfter(long milliseconds) {
        return new PostExecutionControl(milliseconds, null, false);
    }

    public static PostExecutionControl defaultDispatch() {
        return new PostExecutionControl(-1L, null, false);
    }

    private PostExecutionControl(long reprocessDelay, RuntimeException exception, boolean onlyFinalizerHandled) {
        this.reprocessDelay = reprocessDelay;
        this.exception = exception;
        this.onlyFinalizerHandled = onlyFinalizerHandled;
    }

    public boolean reprocessEvent() {
        return reprocessDelay > 0;
    }

    public long getReprocessDelay() {
        return reprocessDelay;
    }

    public boolean isError() {
        return exception != null;
    }

    public boolean isOnlyFinalizerHandled() {
        return onlyFinalizerHandled;
    }

    public RuntimeException getException() {
        return exception;
    }
}
