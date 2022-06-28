package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;

public class VoidPrimaryToSecondaryMapper<P extends HasMetadata>
    implements PrimaryToSecondaryMapper<P> {
  @Override
  public Set<ResourceID> toSecondaryResourceIDs(P primary) {
    throw new UnsupportedOperationException();
  }
}
