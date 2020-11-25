package io.javaoperatorsdk.operator.api;

public class DeleteControl {

    public static final DeleteControl DEFAULT_DELETE = new DeleteControl(true);
    public static final DeleteControl NO_FINALIZER_REMOVAL = new DeleteControl(false);

    private final boolean removeFinalizer;

    private DeleteControl(boolean removeFinalizer) {
        this.removeFinalizer = removeFinalizer;
    }

    public boolean getRemoveFinalizer() {
        return removeFinalizer;
    }

}
