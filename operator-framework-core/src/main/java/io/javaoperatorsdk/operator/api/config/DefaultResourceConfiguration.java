package io.javaoperatorsdk.operator.api.config;


import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;

public class DefaultResourceConfiguration<R extends HasMetadata>
    implements ResourceConfiguration<R> {

  private final Class<R> resourceClass;
  private final String resourceTypeName;
  private final InformerConfiguration<R> informerConfig;

  protected DefaultResourceConfiguration(Class<R> resourceClass,
      InformerConfiguration<R> informerConfig) {
    this.resourceClass = resourceClass;
    this.resourceTypeName = resourceClass.isAssignableFrom(GenericKubernetesResource.class)
        // in general this is irrelevant now for secondary resources it is used just by controller
        // where GenericKubernetesResource now does not apply
        ? GenericKubernetesResource.class.getSimpleName()
        : ReconcilerUtils.getResourceTypeName(resourceClass);
    this.informerConfig = informerConfig;
  }

  @Override
  public String getResourceTypeName() {
    return resourceTypeName;
  }

  @Override
  public Class<R> getResourceClass() {
    return resourceClass;
  }

  @Override
  public InformerConfiguration<R> getInformerConfig() {
    return informerConfig;
  }
}
