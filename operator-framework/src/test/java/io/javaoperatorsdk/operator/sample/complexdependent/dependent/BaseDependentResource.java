package io.javaoperatorsdk.operator.sample.complexdependent.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentCustomResource;

public abstract class BaseDependentResource<R extends HasMetadata>
    extends CRUDKubernetesDependentResource<R, ComplexDependentCustomResource> {

  public static final String K8S_NAME = "app.kubernetes.io/name";
  protected final String component;

  public BaseDependentResource(Class<R> resourceType, String component) {
    super(resourceType);
    this.component = component;
  }

  @Override
  protected Class<ComplexDependentCustomResource> getPrimaryResourceType() {
    return ComplexDependentCustomResource.class;
  }

  protected String name(ComplexDependentCustomResource primary) {
    return String.format("%s-%s", component, primary.getSpec().getProjectId());
  }

  protected ObjectMetaBuilder createMeta(ComplexDependentCustomResource primary) {
    String name = name(primary);
    return new ObjectMetaBuilder()
        .withName(name)
        .withNamespace(primary.getMetadata().getNamespace())
        .addToLabels(K8S_NAME, name);
  }
}
