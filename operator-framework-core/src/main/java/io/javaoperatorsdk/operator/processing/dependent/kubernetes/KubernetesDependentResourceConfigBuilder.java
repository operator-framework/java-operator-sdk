package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;

public final class KubernetesDependentResourceConfigBuilder<R extends HasMetadata> {

  private boolean createResourceOnlyIfNotExistingWithSSA;
  private Boolean useSSA = null;
  private InformerConfiguration<R> informerConfiguration;
  private SSABasedGenericKubernetesResourceMatcher<R> matcher;

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
      InformerConfiguration<R> informerConfiguration) {
    this.informerConfiguration = informerConfiguration;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withSSAMatcher(
      SSABasedGenericKubernetesResourceMatcher<R> matcher) {
    this.matcher = matcher;
    return this;
  }

  public KubernetesDependentResourceConfig<R> build() {
    return new KubernetesDependentResourceConfig<>(
        useSSA, createResourceOnlyIfNotExistingWithSSA, informerConfiguration, matcher);
  }
}
