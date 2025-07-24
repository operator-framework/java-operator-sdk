package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

class NOOPTemporalPrimaryToSecondaryIndex<R extends HasMetadata>
    implements TemporalPrimaryToSecondaryIndex<R> {

  @SuppressWarnings("rawtypes")
  private static final NOOPTemporalPrimaryToSecondaryIndex instance =
      new NOOPTemporalPrimaryToSecondaryIndex();

  @SuppressWarnings("unchecked")
  public static <T extends HasMetadata> NOOPTemporalPrimaryToSecondaryIndex<T> getInstance() {
    return instance;
  }

  private NOOPTemporalPrimaryToSecondaryIndex() {}

  @Override
  public void explicitAddOrUpdate(R resource) {
    // empty method because of noop implementation
  }

  @Override
  public void cleanupForResource(R resource) {
    // empty method because of noop implementation
  }

  @Override
  public Set<ResourceID> getSecondaryResources(ResourceID primary) {
    throw new UnsupportedOperationException();
  }
}
