package io.javaoperatorsdk.operator.processing.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;

/**
 * Helper for the dynamically-created Dependent Resources to make it more explicit that such
 * dependents only to implement
 * {@link DynamicallyCreatedDependentResource#match(Object, Object, HasMetadata, Context)}
 *
 * @param <R> secondary resource type
 * @param <P> primary resource type
 */
public interface DynamicallyCreatedUpdater<R, P extends HasMetadata> extends Updater<R, P> {

  default Matcher.Result<R> match(R actualResource, P primary, Context<P> context) {
    if (!(this instanceof DynamicallyCreatedDependentResource)) {
      throw new IllegalStateException(DynamicallyCreatedUpdater.class.getSimpleName()
          + " interface should only be implemented by "
          + DynamicallyCreatedDependentResource.class.getSimpleName() + " implementations");
    }
    throw new IllegalStateException("This method should not be called from a "
        + DynamicallyCreatedDependentResource.class.getSimpleName() + " implementation");
  }
}
