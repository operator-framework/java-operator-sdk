package io.javaoperatorsdk.operator.workflow.complexdependent.dependent;

import java.util.Map;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.workflow.complexdependent.ComplexWorkflowCustomResource;

public abstract class BaseService extends BaseDependentResource<Service> {

  public BaseService(String component) {
    super(Service.class, component);
  }

  @Override
  protected Service desired(
      ComplexWorkflowCustomResource primary, Context<ComplexWorkflowCustomResource> context) {
    var template =
        ReconcilerUtils.loadYaml(
            Service.class,
            getClass(),
            "/io/javaoperatorsdk/operator/workflow/complexdependent/service.yaml");

    return new ServiceBuilder(template)
        .withMetadata(createMeta(primary).build())
        .editOrNewSpec()
        .withSelector(Map.of(K8S_NAME, name(primary)))
        .endSpec()
        .build();
  }
}
