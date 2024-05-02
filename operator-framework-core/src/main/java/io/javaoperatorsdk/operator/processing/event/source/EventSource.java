package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.health.EventSourceHealthIndicator;
import io.javaoperatorsdk.operator.health.Status;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

/**
 * Creates an event source to trigger your reconciler whenever something happens to a secondary or
 * external resource that should cause a reconciliation of the primary resource. EventSource
 * generalizes the concept of Informers and extends it to external (i.e. non Kubernetes) resources.
 */
public interface EventSource<R, P extends HasMetadata>
    extends LifecycleAware, EventSourceHealthIndicator {

  /**
   * Sets the {@link EventHandler} that is linked to your reconciler when this EventSource is
   * registered.
   *
   * @param handler the {@link EventHandler} associated with your reconciler
   */
  void setEventHandler(EventHandler handler);

  default String name() {
    return generateName(this);
  }

  default EventSourceStartPriority priority() {
    return EventSourceStartPriority.DEFAULT;
  }

  /**
   * Retrieves the resource type associated with this ResourceEventSource
   *
   * @return the resource type associated with this ResourceEventSource
   */
  Class<R> resourceType();

  /**
   * Retrieves this EventSource's configuration if it exists.
   *
   * @return this EventSource's configuration if it exists
   * @since 5.0.0
   */
  @SuppressWarnings({"rawtypes", "unused"})
  default Optional<?> optionalConfiguration() {
    if (this instanceof Configurable configurable) {
      return Optional.ofNullable(configurable.configuration());
    }
    return Optional.empty();
  }

  default Optional<R> getSecondaryResource(P primary) {
    var resources = getSecondaryResources(primary);
    if (resources.isEmpty()) {
      return Optional.empty();
    } else if (resources.size() == 1) {
      return Optional.of(resources.iterator().next());
    } else {
      throw new IllegalStateException("More than 1 secondary resource related to primary");
    }

  }

  Set<R> getSecondaryResources(P primary);

  void setOnAddFilter(OnAddFilter<? super R> onAddFilter);

  void setOnUpdateFilter(OnUpdateFilter<? super R> onUpdateFilter);

  void setOnDeleteFilter(OnDeleteFilter<? super R> onDeleteFilter);

  void setGenericFilter(GenericFilter<? super R> genericFilter);

  @Override
  default Status getStatus() {
    return Status.UNKNOWN;
  }

  static String generateName(EventSource<?, ?> eventSource) {
    return eventSource.getClass().getName() + "@" + Integer.toHexString(eventSource.hashCode());
  }
}
