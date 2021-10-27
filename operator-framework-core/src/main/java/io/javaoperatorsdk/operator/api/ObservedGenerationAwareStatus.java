package io.javaoperatorsdk.operator.api;

import java.util.Optional;

/**
 * A helper base class for status sub-resources classes to extend to support generate awareness.
 */
public class ObservedGenerationAwareStatus implements ObservedGenerationAware {

  private Long observedGeneration;

  @Override
  public void setObservedGeneration(Long generation) {
    this.observedGeneration = generation;
  }

  @Override
  public Optional<Long> getObservedGeneration() {
    return Optional.ofNullable(observedGeneration);
  }
}
