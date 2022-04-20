package io.javaoperatorsdk.operator.processing;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface MultiResourceOwner<R, P extends HasMetadata> extends ResourceOwner<R, P> {

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
}
