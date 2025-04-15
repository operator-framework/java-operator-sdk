package io.javaoperatorsdk.operator.dependent.cleanermanageddependent;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

public class ConfigMapDependentResource
    extends KubernetesDependentResource<ConfigMap, CleanerForManagedDependentCustomResource>
    implements Creator<ConfigMap, CleanerForManagedDependentCustomResource>,
        Updater<ConfigMap, CleanerForManagedDependentCustomResource>,
        Deleter<CleanerForManagedDependentCustomResource> {

  private static final AtomicInteger numberOfCleanupExecutions = new AtomicInteger(0);

  @Override
  protected ConfigMap desired(
      CleanerForManagedDependentCustomResource primary,
      Context<CleanerForManagedDependentCustomResource> context) {

    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(primary.getMetadata().getName());
    configMap.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    HashMap<String, String> data = new HashMap<>();
    data.put("key1", "val1");
    configMap.setData(data);
    return configMap;
  }

  @Override
  public void delete(
      CleanerForManagedDependentCustomResource primary,
      Context<CleanerForManagedDependentCustomResource> context) {
    super.delete(primary, context);
    numberOfCleanupExecutions.incrementAndGet();
  }

  @Override
  protected boolean addOwnerReference() {
    return true;
  }

  public static int getNumberOfCleanupExecutions() {
    return numberOfCleanupExecutions.get();
  }
}
