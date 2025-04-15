package io.javaoperatorsdk.operator.sample.dependentresource;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.sample.Utils;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;

import static io.javaoperatorsdk.operator.ReconcilerUtils.loadYaml;
import static io.javaoperatorsdk.operator.sample.Utils.deploymentName;
import static io.javaoperatorsdk.operator.sample.Utils.serviceName;
import static io.javaoperatorsdk.operator.sample.WebPageManagedDependentsReconciler.SELECTOR;

// this annotation only activates when using managed dependents and is not otherwise needed
@KubernetesDependent(informer = @Informer(labelSelector = SELECTOR))
public class ServiceDependentResource
    extends io.javaoperatorsdk.operator.processing.dependent.kubernetes
            .CRUDKubernetesDependentResource<
        Service, WebPage> {

  @Override
  protected Service desired(WebPage webPage, Context<WebPage> context) {
    Map<String, String> serviceLabels = new HashMap<>();
    serviceLabels.put(SELECTOR, "true");
    Service service = loadYaml(Service.class, Utils.class, "service.yaml");
    service.getMetadata().setName(serviceName(webPage));
    service.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
    service.getMetadata().setLabels(serviceLabels);
    Map<String, String> labels = new HashMap<>();
    labels.put("app", deploymentName(webPage));
    service.getSpec().setSelector(labels);
    return service;
  }
}
