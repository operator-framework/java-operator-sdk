package io.javaoperatorsdk.operator.processing.dependent.kubernetes;


import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;


public class KubernetesDependentResourceConfig<R extends HasMetadata> {

  public static final boolean DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA = true;

  private final Boolean useSSA;
  private final boolean createResourceOnlyIfNotExistingWithSSA;
  private final InformerConfiguration<R> informerConfig;

  public KubernetesDependentResourceConfig(
      Boolean useSSA,
      boolean createResourceOnlyIfNotExistingWithSSA,
      InformerConfiguration<R> informerConfig) {
    this.useSSA = useSSA;
    this.createResourceOnlyIfNotExistingWithSSA = createResourceOnlyIfNotExistingWithSSA;
    this.informerConfig = informerConfig;
  }

  public boolean createResourceOnlyIfNotExistingWithSSA() {
    return createResourceOnlyIfNotExistingWithSSA;
  }

  public Boolean useSSA() {
    return useSSA;
  }

  public InformerConfiguration<R> informerConfig() {
    return informerConfig;
  }
}
