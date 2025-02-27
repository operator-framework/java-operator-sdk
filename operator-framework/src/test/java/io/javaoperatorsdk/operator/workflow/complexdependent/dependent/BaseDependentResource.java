package io.javaoperatorsdk.operator.workflow.complexdependent.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.workflow.complexdependent.ComplexWorkflowCustomResource;

public abstract class BaseDependentResource<R extends HasMetadata>
    extends CRUDKubernetesDependentResource<R, ComplexWorkflowCustomResource> {

  public static final String K8S_NAME = "app.kubernetes.io/name";
  protected final String component;

  public BaseDependentResource(Class<R> resourceType, String component) {
    super(resourceType);
    this.component = component;
  }

  protected String name(ComplexWorkflowCustomResource primary) {
    return String.format("%s-%s", component, primary.getSpec().getProjectId());
  }

  protected ObjectMetaBuilder createMeta(ComplexWorkflowCustomResource primary) {
    String name = name(primary);
    return new ObjectMetaBuilder().withName(name)
        .withNamespace(primary.getMetadata().getNamespace()).addToLabels(K8S_NAME, name);
  }
}
