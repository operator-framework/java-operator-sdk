package io.javaoperatorsdk.operator.processing.dependent.kubernetes;


import io.fabric8.kubernetes.api.model.HasMetadata;

public final class KubernetesDependentResourceConfigBuilder<R extends HasMetadata> {

  private boolean createResourceOnlyIfNotExistingWithSSA;
  private Boolean useSSA = null;
  private KubernetesDependentInformerConfig<R> kubernetesDependentInformerConfig;

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
      KubernetesDependentInformerConfig<R> kubernetesDependentInformerConfig) {
    this.kubernetesDependentInformerConfig = kubernetesDependentInformerConfig;
    return this;
  }

  public KubernetesDependentResourceConfig<R> build() {
    return new KubernetesDependentResourceConfig<>(
        useSSA, createResourceOnlyIfNotExistingWithSSA,
        kubernetesDependentInformerConfig);
  }
}
