package io.javaoperatorsdk.operator.api;

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
  public Long getObservedGeneration() {
    return observedGeneration;
  }
}
