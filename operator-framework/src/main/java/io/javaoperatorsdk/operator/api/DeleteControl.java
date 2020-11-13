package com.github.containersolutions.operator.api;

public class DeleteControl {

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
        return this;
    }

}
