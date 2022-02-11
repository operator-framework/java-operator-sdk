package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesDependentResource;

public class ServiceDependentResource extends KubernetesDependentResource<Service, Tomcat> {

  public ServiceDependentResource() {
    super(Service.class, (tomcat, context) -> {
      final ObjectMeta tomcatMetadata = tomcat.getMetadata();
      return new ServiceBuilder(TomcatReconciler.loadYaml(Service.class, "service.yaml"))
          .editMetadata()
          .withName(tomcatMetadata.getName())
          .withNamespace(tomcatMetadata.getNamespace())
          .endMetadata()
          .editSpec()
          .addToSelector("app", tomcatMetadata.getName())
          .endSpec()
          .build();
    });
  }
}
