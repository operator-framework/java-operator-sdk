/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.sample.simple;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration(generationAwareEventProcessing = false)
public class TestCustomReconciler
    implements Reconciler<TestCustomResource>, Cleaner<TestCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(TestCustomReconciler.class);

  public static final String CRD_NAME = CustomResource.getCRDName(TestCustomResource.class);
  public static final String FINALIZER_NAME = CRD_NAME + "/finalizer";

  private final KubernetesClient kubernetesClient;
  private final boolean updateStatus;

  public TestCustomReconciler(KubernetesClient kubernetesClient) {
    this(kubernetesClient, true);
  }

  public TestCustomReconciler(KubernetesClient kubernetesClient, boolean updateStatus) {
    this.kubernetesClient = kubernetesClient;
    this.updateStatus = updateStatus;
  }

  @Override
  public DeleteControl cleanup(TestCustomResource resource, Context<TestCustomResource> context) {
    var statusDetails =
        kubernetesClient
            .configMaps()
            .inNamespace(resource.getMetadata().getNamespace())
            .withName(resource.getSpec().getConfigMapName())
            .delete();
    if (statusDetails.size() == 1 && statusDetails.get(0).getCauses().isEmpty()) {
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
          .resource(existingConfigMap)
          .createOrReplace();
    } else {
      Map<String, String> labels = new HashMap<>();
      labels.put("managedBy", TestCustomReconciler.class.getSimpleName());
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
          .resource(newConfigMap)
          .createOrReplace();
    }
    if (updateStatus) {
      if (resource.getStatus() == null) {
        resource.setStatus(new TestCustomResourceStatus());
      }
      resource.getStatus().setConfigMapStatus("ConfigMap Ready");
    }
    return UpdateControl.patchResource(resource);
  }

  private Map<String, String> configMapData(TestCustomResource resource) {
    Map<String, String> data = new HashMap<>();
    data.put(resource.getSpec().getKey(), resource.getSpec().getValue());
    return data;
  }
}
