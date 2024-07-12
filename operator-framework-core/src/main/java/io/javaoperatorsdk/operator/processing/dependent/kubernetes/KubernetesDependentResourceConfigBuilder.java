package io.javaoperatorsdk.operator.processing.dependent.kubernetes;


import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfigHolder;

public final class KubernetesDependentResourceConfigBuilder<R extends HasMetadata> {

  private boolean createResourceOnlyIfNotExistingWithSSA;
  private Boolean useSSA = null;
  private InformerConfigHolder<R> informerConfigHolder;

  public KubernetesDependentResourceConfigBuilder() {}

  @SuppressWarnings("unused")
  public KubernetesDependentResourceConfigBuilder<R> withCreateResourceOnlyIfNotExistingWithSSA(
      boolean createResourceOnlyIfNotExistingWithSSA) {
    this.createResourceOnlyIfNotExistingWithSSA = createResourceOnlyIfNotExistingWithSSA;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withUseSSA(boolean useSSA) {
    this.useSSA = useSSA;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withKubernetesDependentInformerConfig(
      InformerConfigHolder<R> informerConfigHolder) {
    this.informerConfigHolder = informerConfigHolder;
    return this;
  }

  public KubernetesDependentResourceConfig<R> build() {
    return new KubernetesDependentResourceConfig<>(
        useSSA, createResourceOnlyIfNotExistingWithSSA,
        informerConfigHolder);
  }
}
