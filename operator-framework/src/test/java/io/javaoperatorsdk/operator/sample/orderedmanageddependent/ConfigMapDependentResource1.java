package io.javaoperatorsdk.operator.sample.orderedmanageddependent;

import java.util.HashMap;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryToSecondaryMapper;

public class ConfigMapDependentResource1 extends
        CRUKubernetesDependentResource<ConfigMap, OrderedManagedDependentCustomResource>
        implements PrimaryToSecondaryMapper<OrderedManagedDependentCustomResource> {

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
  protected ConfigMap desired(OrderedManagedDependentCustomResource primary,
      Context<OrderedManagedDependentCustomResource> context) {

    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(primary.getMetadata().getName() + "1");
    configMap.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    HashMap<String, String> data = new HashMap<>();
    data.put("key1", "val1");
    configMap.setData(data);
    return configMap;
  }

  @Override
  public ResourceID associatedSecondaryID(OrderedManagedDependentCustomResource primary) {
    return new ResourceID(primary.getMetadata().getName() + "1", primary.getMetadata().getNamespace());
  }
}
