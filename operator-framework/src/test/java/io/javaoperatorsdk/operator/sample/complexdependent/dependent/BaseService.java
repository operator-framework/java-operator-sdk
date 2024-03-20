package io.javaoperatorsdk.operator.sample.complexdependent.dependent;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentCustomResource;
import java.util.Map;

public abstract class BaseService extends BaseDependentResource<Service> {

  public BaseService(String component) {
    super(Service.class, component);
  }

  @Override
  protected Service desired(ComplexDependentCustomResource primary,
      Context<ComplexDependentCustomResource> context) {
    var template = ReconcilerUtils.loadYaml(Service.class, getClass(), "service.yaml");

    return new ServiceBuilder(template)
        .withMetadata(createMeta(primary).build())
        .editOrNewSpec()
        .withSelector(Map.of(K8S_NAME, name(primary)))
        .endSpec()
        .build();
  }
}
