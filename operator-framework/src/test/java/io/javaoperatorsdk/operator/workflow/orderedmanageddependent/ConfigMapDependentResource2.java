package io.javaoperatorsdk.operator.workflow.orderedmanageddependent;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(informer = @Informer(labelSelector = "dependent = cm2"))
public class ConfigMapDependentResource2
    extends CRUDKubernetesDependentResource<ConfigMap, OrderedManagedDependentCustomResource> {

  @Override
  public ReconcileResult<ConfigMap> reconcile(
      OrderedManagedDependentCustomResource primary,
      Context<OrderedManagedDependentCustomResource> context) {
    OrderedManagedDependentTestReconciler.dependentExecution.add(this.getClass());
    return super.reconcile(primary, context);
  }

  @Override
  protected ConfigMap desired(
      OrderedManagedDependentCustomResource primary,
      Context<OrderedManagedDependentCustomResource> context) {

    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    Map<String, String> labels = new HashMap<>();
    labels.put("dependent", "cm2");
    configMap.getMetadata().setLabels(labels);
    configMap.getMetadata().setName(primary.getMetadata().getName() + "2");
    configMap.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    HashMap<String, String> data = new HashMap<>();
    data.put("key2", "val2");
    configMap.setData(data);
    return configMap;
  }
}
