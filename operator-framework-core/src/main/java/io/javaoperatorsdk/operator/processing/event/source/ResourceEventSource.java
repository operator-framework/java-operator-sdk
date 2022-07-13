package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.ResourceOwner;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

public interface ResourceEventSource<R, P extends HasMetadata> extends EventSource,
    ResourceOwner<R, P> {

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

  void setOnAddFilter(OnAddFilter<R> onAddFilter);

  void setOnUpdateFilter(OnUpdateFilter<R> onUpdateFilter);

  void setOnDeleteFilter(OnDeleteFilter<R> onDeleteFilter);

  void setGenericFilter(GenericFilter<R> genericFilter);
}
