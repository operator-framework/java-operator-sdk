package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

class NOOPPrimaryToSecondaryIndex<R extends HasMetadata> implements PrimaryToSecondaryIndex<R> {

  @SuppressWarnings("rawtypes")
  private static final NOOPPrimaryToSecondaryIndex instance = new NOOPPrimaryToSecondaryIndex();

  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata> NOOPPrimaryToSecondaryIndex<T> getInstance() {
    return instance;
  }

  private NOOPPrimaryToSecondaryIndex() {}

  @Override
  public void onAddOrUpdate(R resource) {
    // empty method because of noop implementation
  }

  @Override
  public void onDelete(R resource) {
    // empty method because of noop implementation
  }

  @Override
  public Set<ResourceID> getSecondaryResources(ResourceID primary) {
    throw new UnsupportedOperationException();
  }
}
