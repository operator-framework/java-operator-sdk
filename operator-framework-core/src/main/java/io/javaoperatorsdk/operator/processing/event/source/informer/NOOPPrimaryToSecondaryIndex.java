package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;


class NOOPPrimaryToSecondaryIndex<R extends HasMetadata>
    implements PrimaryToSecondaryIndex<R> {

  @SuppressWarnings("rawtypes")
  private final static NOOPPrimaryToSecondaryIndex instance = new NOOPPrimaryToSecondaryIndex();

  public static <T extends HasMetadata> NOOPPrimaryToSecondaryIndex<T> getInstance() {
    return instance;
  }

  private NOOPPrimaryToSecondaryIndex() {
  }

  @Override
  public void onAddOrUpdate(R resource) {}

  @Override
  public void onDelete(R resource) {}

  @Override
  public Set<ResourceID> getSecondaryResources(ResourceID primary) {
    throw new IllegalStateException("Should not be called");
  }
}
