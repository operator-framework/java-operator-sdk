package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Builder;

public class ServiceDependentResource
    implements DependentResource<Service, Tomcat>, Builder<Service, Tomcat> {

  @Override
  public Service buildFor(Tomcat tomcat, Context context) {
    final ObjectMeta tomcatMetadata = tomcat.getMetadata();
    final Service service =
        new ServiceBuilder(TomcatReconciler.loadYaml(Service.class, "service.yaml"))
            .editMetadata()
            .withName(tomcatMetadata.getName())
            .withNamespace(tomcatMetadata.getNamespace())
            .endMetadata()
            .editSpec()
            .addToSelector("app", tomcatMetadata.getName())
            .endSpec()
            .build();
    return service;
  }
}
