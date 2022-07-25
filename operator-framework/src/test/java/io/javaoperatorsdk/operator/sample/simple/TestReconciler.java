package io.javaoperatorsdk.operator.sample.simple;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.junit.KubernetesClientAware;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(generationAwareEventProcessing = false)
public class TestReconciler
    implements Reconciler<TestCustomResource>, Cleaner<TestCustomResource>,
    TestExecutionInfoProvider,
    KubernetesClientAware {

  private static final Logger log = LoggerFactory.getLogger(TestReconciler.class);

  public static final String FINALIZER_NAME =
      ReconcilerUtils.getDefaultFinalizerName(TestCustomResource.class);

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final AtomicInteger numberOfCleanupExecutions = new AtomicInteger(0);
  private KubernetesClient kubernetesClient;
  private volatile boolean updateStatus;
  private volatile boolean patchStatus;

  public TestReconciler(boolean updateStatus) {
    this(updateStatus, false);
  }

  public TestReconciler(boolean updateStatus, boolean patchStatus) {
    this.updateStatus = updateStatus;
    this.patchStatus = patchStatus;
  }

  public TestReconciler setPatchStatus(boolean patchStatus) {
    this.patchStatus = patchStatus;
    return this;
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
  public DeleteControl cleanup(
      TestCustomResource resource, Context<TestCustomResource> context) {
    numberOfCleanupExecutions.incrementAndGet();

    var statusDetail = kubernetesClient
        .configMaps()
        .inNamespace(resource.getMetadata().getNamespace())
        .withName(resource.getSpec().getConfigMapName())
        .delete();

    if (statusDetail.size() == 1 && statusDetail.get(0).getCauses().isEmpty()) {
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
  public UpdateControl<TestCustomResource> reconcile(
      TestCustomResource resource, Context<TestCustomResource> context) {
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
    if (patchStatus) {
      return UpdateControl.patchStatus(resource);
    } else {
      return UpdateControl.updateStatus(resource);
    }
  }

  private Map<String, String> configMapData(TestCustomResource resource) {
    Map<String, String> data = new HashMap<>();
    data.put(resource.getSpec().getKey(), resource.getSpec().getValue());
    return data;
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public int getNumberOfCleanupExecutions() {
    return numberOfCleanupExecutions.get();
  }
}
