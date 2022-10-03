package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

public interface EventSourceAware<R, P extends HasMetadata> {

  /**
   * Dependent resources are designed to by default provide event sources. There are cases where it
   * might not:
   * <ul>
   * <li>If an event source is shared between multiple dependent resources. In this case only one or
   * none of the dependent resources sharing the event source should provide one.</li>
   * <li>Some special implementation of an event source. That just execute some action might not
   * provide one.</li>
   * </ul>
   *
   * @param context context of event source initialization
   * @return an optional event source
   */
  default Optional<ResourceEventSource<R, P>> eventSource(EventSourceContext<P> context) {
    return getUsedEventSourceName().map(
        name -> context.getEventSourceRetriever().getResourceEventSourceFor(resourceType(), name));
  }

  Class<R> resourceType();

  void useEventSourceNamed(String eventSourceName);

  default Optional<String> getUsedEventSourceName() {
    return Optional.empty();
  }
}
