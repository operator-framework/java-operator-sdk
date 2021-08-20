package io.javaoperatorsdk.operator.sample.simple;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller(generationAwareEventProcessing = false)
public class TestCustomResourceController implements ResourceController<TestCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(TestCustomResourceController.class);

  public static final String CRD_NAME = CustomResource.getCRDName(TestCustomResource.class);
  public static final String FINALIZER_NAME = CRD_NAME + "/finalizer";

  private final KubernetesClient kubernetesClient;
  private final boolean updateStatus;

  public TestCustomResourceController(KubernetesClient kubernetesClient) {
    this(kubernetesClient, true);
  }

  public TestCustomResourceController(KubernetesClient kubernetesClient, boolean updateStatus) {
    this.kubernetesClient = kubernetesClient;
    this.updateStatus = updateStatus;
  }

  @Override
  public DeleteControl deleteResource(
      TestCustomResource resource, Context<TestCustomResource> context) {
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
    return DeleteControl.DEFAULT_DELETE;
  }

  @Override
  public UpdateControl<TestCustomResource> createOrUpdateResource(
      TestCustomResource resource, Context<TestCustomResource> context) {
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
      labels.put("managedBy", TestCustomResourceController.class.getSimpleName());
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
    return UpdateControl.updateCustomResource(resource);
  }

  private Map<String, String> configMapData(TestCustomResource resource) {
    Map<String, String> data = new HashMap<>();
    data.put(resource.getSpec().getKey(), resource.getSpec().getValue());
    return data;
  }
}
