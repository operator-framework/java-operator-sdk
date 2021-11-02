package io.javaoperatorsdk.operator.sample.simple;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Controller;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Controller(generationAwareEventProcessing = false)
public class TestReconciler
    implements Reconciler<TestCustomResource>, TestExecutionInfoProvider,
    KubernetesClientAware {

  private static final Logger log = LoggerFactory.getLogger(TestReconciler.class);

  public static final String FINALIZER_NAME =
      ControllerUtils.getDefaultFinalizerName(CustomResource.getCRDName(TestCustomResource.class));

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient kubernetesClient;
  private boolean updateStatus;

  public TestReconciler() {
    this(true);
  }

  public TestReconciler(boolean updateStatus) {
    this.updateStatus = updateStatus;
  }

  public boolean isUpdateStatus() {
    return updateStatus;
  }

  public void setUpdateStatus(boolean updateStatus) {
    this.updateStatus = updateStatus;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  @Override
  public void setKubernetesClient(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public DeleteControl deleteResources(
      TestCustomResource resource, Context context) {
    Boolean delete =
        kubernetesClient
            .configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getSpec().getConfigMapName())
            .delete();
    if (delete) {
      log.info(
          "Deleted ConfigMap {} for resource: {}",
          resource.getSpec().getConfigMapName(),
          resource.getMetadata().getName());
    } else {
      log.error(
          "Failed to delete ConfigMap {} for resource: {}",
          resource.getSpec().getConfigMapName(),
          resource.getMetadata().getName());
    }
    return DeleteControl.defaultDelete();
  }

  @Override
  public UpdateControl<TestCustomResource> createOrUpdateResources(
      TestCustomResource resource, Context context) {
    numberOfExecutions.addAndGet(1);
    if (!resource.getMetadata().getFinalizers().contains(FINALIZER_NAME)) {
      throw new IllegalStateException("Finalizer is not present.");
    }

    ConfigMap existingConfigMap =
        kubernetesClient
            .configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getSpec().getConfigMapName())
            .get();

    if (existingConfigMap != null) {
      existingConfigMap.setData(configMapData(resource));
      // existingConfigMap.getMetadata().setResourceVersion(null);
      kubernetesClient
          .configMaps()
          .inNamespace(resource.getMetadata().getNamespace())
          .withName(existingConfigMap.getMetadata().getName())
          .createOrReplace(existingConfigMap);
    } else {
      Map<String, String> labels = new HashMap<>();
      labels.put("managedBy", TestReconciler.class.getSimpleName());
      ConfigMap newConfigMap =
          new ConfigMapBuilder()
              .withMetadata(
                  new ObjectMetaBuilder()
                      .withName(resource.getSpec().getConfigMapName())
                      .withNamespace(resource.getMetadata().getNamespace())
                      .withLabels(labels)
                      .build())
              .withData(configMapData(resource))
              .build();
      kubernetesClient
          .configMaps()
          .inNamespace(resource.getMetadata().getNamespace())
          .createOrReplace(newConfigMap);
    }
    if (updateStatus) {
      if (resource.getStatus() == null) {
        resource.setStatus(new TestCustomResourceStatus());
      }
      resource.getStatus().setConfigMapStatus("ConfigMap Ready");
    }
    return UpdateControl.updateStatusSubResource(resource);
  }

  private Map<String, String> configMapData(TestCustomResource resource) {
    Map<String, String> data = new HashMap<>();
    data.put(resource.getSpec().getKey(), resource.getSpec().getValue());
    return data;
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
