package io.javaoperatorsdk.operator.sample.orderedmanageddependent;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceIDMatcherDiscriminator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@KubernetesDependent(labelSelector = "dependent = cm1",
    resourceDiscriminator = ConfigMapDependentResource1.CM1ResourceDiscriminator.class)
public class ConfigMapDependentResource1 extends
    CRUDKubernetesDependentResource<ConfigMap, OrderedManagedDependentCustomResource> {

  public ConfigMapDependentResource1() {
    super(ConfigMap.class);
  }

  @Override
  public ReconcileResult<ConfigMap> reconcile(OrderedManagedDependentCustomResource primary,
      Context<OrderedManagedDependentCustomResource> context) {
    OrderedManagedDependentTestReconciler.dependentExecution.add(this.getClass());
    return super.reconcile(primary, context);
  }

  @Override
  protected ConfigMap createDesired(OrderedManagedDependentCustomResource primary,
      Context<OrderedManagedDependentCustomResource> context) {

    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    Map<String, String> labels = new HashMap<>();
    labels.put("dependent", "cm1");
    configMap.getMetadata().setLabels(labels);
    configMap.getMetadata().setName(primary.getMetadata().getName() + "1");
    configMap.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    HashMap<String, String> data = new HashMap<>();
    data.put("key1", "val1");
    configMap.setData(data);
    return configMap;
  }

  public static class CM1ResourceDiscriminator
      extends ResourceIDMatcherDiscriminator<ConfigMap, OrderedManagedDependentCustomResource> {
    public CM1ResourceDiscriminator() {
      super(p -> new ResourceID(p.getMetadata().getName() + "1", p.getMetadata().getNamespace()));
    }
  }

}
