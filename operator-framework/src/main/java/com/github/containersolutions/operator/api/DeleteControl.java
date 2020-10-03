package com.github.containersolutions.operator.api;

public class DeleteControl extends ReprocessControl {

    public static DeleteControl defaultDelete() {
        return new DeleteControl();
    }

    public static DeleteControl noFinalizerRemoval() {
        DeleteControl deleteControl = new DeleteControl();
        deleteControl.setRemoveFinalizer(false);
        return deleteControl;
    }

    private boolean removeFinalizer = true;

    public boolean getRemoveFinalizer() {
        return removeFinalizer;
    }

    public DeleteControl setRemoveFinalizer(boolean removeFinalizer) {
        this.removeFinalizer = removeFinalizer;
        validate();
        return this;
    }

    public ReprocessControl reprocessAfter(long milliseconds) {
        super.reprocessAfter(milliseconds);
        validate();
        return this;
    }

    private void validate() {
        if (super.isForReprocess() && removeFinalizer) {
            throw new IllegalStateException("If finalizer is to be removed, cannot reprocess.");
        }
    }
}
