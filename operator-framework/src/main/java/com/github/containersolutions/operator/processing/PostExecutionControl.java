package com.github.containersolutions.operator.processing;

public final class PostExecutionControl {

    private final boolean onlyFinalizerHandled;

    private PostExecutionControl(boolean onlyFinalizerHandled) {
        this.onlyFinalizerHandled = onlyFinalizerHandled;
    }

    public static PostExecutionControl onlyFinalizerAdded() {
        return new PostExecutionControl(true);
    }

    public static PostExecutionControl defaultDispatch() {
        return new PostExecutionControl(false);
    }

    public boolean isOnlyFinalizerHandled() {
        return onlyFinalizerHandled;
    }
}
