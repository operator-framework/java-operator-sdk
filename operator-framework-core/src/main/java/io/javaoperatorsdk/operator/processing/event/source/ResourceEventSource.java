package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public interface ResourceEventSource<R, P extends HasMetadata> extends EventSource {

  /**
   * Retrieves the resource type associated with this ResourceEventSource
   *
   * @return the resource type associated with this ResourceEventSource
   */
  Class<R> resourceType();

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

}
