package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class DeleteControl extends BaseControl<DeleteControl> {

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
   * In some corner cases it might take some time while some secondary resources are cleaned up, in
   * that case delete can be instructed and return for {@link Cleaner#cleanup(HasMetadata, Context)}
   * method, that will be triggered again by an EventSource when the secondary resource is deleted.
   * After all resources are cleaned up such async way it is safe to remove the finalizer, thus
   * return defaultDelete from cleanup method.
   *
   * @return delete control that will not remove finalizer.
   */
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
