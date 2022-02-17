package io.javaoperatorsdk.operator.sample;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class ServiceDependentResource extends KubernetesDependentResource<Service, Tomcat> {

  @Override
  protected Optional<Service> desired(Tomcat tomcat, Context context) {
    final ObjectMeta tomcatMetadata = tomcat.getMetadata();
    return Optional
        .of(new ServiceBuilder(ReconcilerUtils.loadYaml(Service.class, getClass(), "service.yaml"))
            .editMetadata()
            .withName(tomcatMetadata.getName())
            .withNamespace(tomcatMetadata.getNamespace())
            .endMetadata()
            .editSpec()
            .addToSelector("app", tomcatMetadata.getName())
            .endSpec()
            .build());
  }

}
