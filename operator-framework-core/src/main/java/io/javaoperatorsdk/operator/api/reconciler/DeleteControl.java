package io.javaoperatorsdk.operator.api.reconciler;

public class DeleteControl extends BaseControl<DeleteControl> {

  private final boolean removeFinalizer;

  private DeleteControl(boolean removeFinalizer) {
    this.removeFinalizer = removeFinalizer;
  }

  public static DeleteControl defaultDelete() {
    return new DeleteControl(true);
  }

  public static DeleteControl noFinalizerRemoval() {
    return new DeleteControl(false);
  }

  public boolean isRemoveFinalizer() {
    return removeFinalizer;
  }

  @Override
  public DeleteControl rescheduleAfter(long delay) {
    if (removeFinalizer) {
      throw new IllegalStateException("Cannot reschedule cleanup if removing finalizer");
    }
    return super.rescheduleAfter(delay);
  }
}
