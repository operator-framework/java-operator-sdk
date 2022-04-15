package io.javaoperatorsdk.operator.processing;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;

public interface MultiResourceOwner<R, P extends HasMetadata> extends ResourceOwner<R, P> {

  default Optional<R> getSecondaryResource(P primary) {
    var list = getSecondaryResources(primary);
    if (list.isEmpty()) {
      return Optional.empty();
    } else if (list.size() == 1) {
      return Optional.of(list.get(0));
    } else {
      throw new IllegalStateException("More than 1 secondary resource related to primary");
    }

  }

  List<R> getSecondaryResources(P primary);
}
