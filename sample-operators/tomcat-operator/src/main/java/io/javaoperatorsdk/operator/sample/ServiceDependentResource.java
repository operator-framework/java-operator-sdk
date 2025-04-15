package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(
    informer = @Informer(labelSelector = "app.kubernetes.io/managed-by=tomcat-operator"))
public class ServiceDependentResource extends CRUDKubernetesDependentResource<Service, Tomcat> {

  @Override
  protected Service desired(Tomcat tomcat, Context<Tomcat> context) {
    final ObjectMeta tomcatMetadata = tomcat.getMetadata();
    return new ServiceBuilder(ReconcilerUtils.loadYaml(Service.class, getClass(), "service.yaml"))
        .editMetadata()
        .withName(tomcatMetadata.getName())
        .withNamespace(tomcatMetadata.getNamespace())
        .addToLabels("app.kubernetes.io/managed-by", "tomcat-operator")
        .endMetadata()
        .editSpec()
        .addToSelector("app", tomcatMetadata.getName())
        .endSpec()
        .build();
  }
}
