package io.javaoperatorsdk.operator.processing;

import io.javaoperatorsdk.operator.api.UpdateControl;

import java.util.Optional;

public final class PostExecutionControl {

    private final boolean onlyFinalizerHandled;

    private final UpdateControl updateControl;

    private PostExecutionControl(boolean onlyFinalizerHandled, UpdateControl updateControl) {
        this.onlyFinalizerHandled = onlyFinalizerHandled;
        this.updateControl = updateControl;
    }

    public static PostExecutionControl onlyFinalizerAdded() {
        return new PostExecutionControl(true,null);
    }

    public static PostExecutionControl defaultDispatch() {
        return new PostExecutionControl(false,null);
    }
    public static PostExecutionControl dispatchWithUpdateControl(UpdateControl updateControl) {
        return new PostExecutionControl(false,updateControl);
    }

    public boolean isOnlyFinalizerHandled() {
        return onlyFinalizerHandled;
    }

    public Optional<UpdateControl> getUpdateControl() {
        return Optional.ofNullable(updateControl);
    }
}
