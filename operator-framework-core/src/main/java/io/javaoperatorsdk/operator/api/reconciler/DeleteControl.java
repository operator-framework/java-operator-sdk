package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class DeleteControl<P extends HasMetadata> extends BaseControl<DeleteControl<P>, P> {

  private final boolean removeFinalizer;

  private DeleteControl(boolean removeFinalizer) {
    this.removeFinalizer = removeFinalizer;
  }

  /**
   * @return delete control that will remove finalizer.
   */
  public static DeleteControl defaultDelete() {
    return new DeleteControl(true);
  }

  /**
   * In some corner cases it might take some time for secondary resources to be cleaned up. In such
   * situation, the reconciler shouldn't remove the finalizer until it is safe for the primary
   * resource to be deleted (because, e.g., secondary resources being removed might need its
   * information to proceed correctly). Using this method will instruct the reconciler to leave the
   * finalizer in place, presumably to wait for a new reconciliation triggered by event sources on
   * the secondary resources (e.g. when they are effectively deleted/cleaned-up) to check whether it
   * is now safe to delete the primary resource and therefore, remove its finalizer by returning
   * {@link #defaultDelete()} then.
   *
   * @return delete control that will not remove finalizer.
   */
  public static <T extends HasMetadata> DeleteControl<T> noFinalizerRemoval() {
    return new DeleteControl(false);
  }

  public boolean isRemoveFinalizer() {
    return removeFinalizer;
  }

  @Override
  public DeleteControl<P> rescheduleAfter(long delay) {
    if (removeFinalizer) {
      throw new IllegalStateException("Cannot reschedule cleanup if removing finalizer");
    }
    return super.rescheduleAfter(delay);
  }
}
