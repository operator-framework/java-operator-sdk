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
package io.javaoperatorsdk.operator.baseapi.deployment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.deployment.DeploymentReconciler.STATUS_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class KubernetesResourceStatusUpdateIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new DeploymentReconciler()).build();

  @Test
  void testReconciliationOfNonCustomResourceAndStatusUpdate() {
    var deployment = operator.create(testDeployment());
    await()
        .atMost(120, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var d = operator.get(Deployment.class, deployment.getMetadata().getName());
              assertThat(d.getStatus()).isNotNull();
              assertThat(d.getStatus().getConditions()).isNotNull();
              // wait until the pod is ready, if not this is causing some test stability issues with
              // namespace cleanup in k8s version 1.22
              assertThat(d.getStatus().getReadyReplicas()).isGreaterThanOrEqualTo(1);
              assertThat(
                      d.getStatus().getConditions().stream()
                          .filter(c -> c.getMessage().equals(STATUS_MESSAGE))
                          .count())
                  .isEqualTo(1);
            });
  }

  private Deployment testDeployment() {
    Deployment resource = new Deployment();
    Map<String, String> labels = new HashMap<>();
    labels.put("test", "KubernetesResourceStatusUpdateIT");
    resource.setMetadata(
        new ObjectMetaBuilder().withName("test-deployment").withLabels(labels).build());
    DeploymentSpec spec = new DeploymentSpec();
    resource.setSpec(spec);
    spec.setReplicas(1);
    var labelSelector = new HashMap<String, String>();
    labelSelector.put("app", "nginx");
    spec.setSelector(new LabelSelector(null, labelSelector));
    PodTemplateSpec podTemplate = new PodTemplateSpec();
    spec.setTemplate(podTemplate);

    podTemplate.setMetadata(new ObjectMeta());
    podTemplate.getMetadata().setLabels(labelSelector);
    podTemplate.setSpec(new PodSpec());

    Container container = new Container();
    container.setName("nginx");
    container.setImage("nginx:1.21.4");
    ContainerPort port = new ContainerPort();
    port.setContainerPort(80);
    container.setPorts(List.of(port));

    podTemplate.getSpec().setContainers(List.of(container));
    return resource;
  }
}
