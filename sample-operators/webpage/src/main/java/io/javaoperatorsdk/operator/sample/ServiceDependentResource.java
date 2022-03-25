package io.javaoperatorsdk.operator.sample;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;
import static io.javaoperatorsdk.operator.sample.Utils.serviceName;

// this annotation only activates when using managed dependents and is not otherwise needed
@KubernetesDependent(labelSelector = WebPageManagedDependentsReconciler.SELECTOR)
class ServiceDependentResource extends CRUKubernetesDependentResource<Service, WebPage> {

  public ServiceDependentResource() {
    super(Service.class);
  }

  @Override
  protected Service desired(WebPage webPage, Context<WebPage> context) {
    Service service = loadYaml(Service.class, getClass(), "service.yaml");
    service.getMetadata().setName(serviceName(webPage));
    service.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
    Map<String, String> labels = new HashMap<>();
    labels.put("app", deploymentName(webPage));
    service.getSpec().setSelector(labels);
    return service;
  }
}
