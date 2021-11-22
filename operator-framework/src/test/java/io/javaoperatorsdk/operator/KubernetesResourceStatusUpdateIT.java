package io.javaoperatorsdk.operator;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.deployment.DeploymentReconciler;

import static io.javaoperatorsdk.operator.sample.deployment.DeploymentReconciler.STATUS_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class KubernetesResourceStatusUpdateIT {

  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withReconciler(new DeploymentReconciler())
          .build();

  @Test
  public void testReconciliationOfNonCustomResourceAndStatusUpdate() {
    var deployment = operator.create(Deployment.class, testDeployment());
    await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
      var d = operator.get(Deployment.class, deployment.getMetadata().getName());
      assertThat(d.getStatus()).isNotNull();
      assertThat(d.getStatus().getConditions()).isNotNull();
      assertThat(
          d.getStatus().getConditions().stream().filter(c -> c.getMessage().equals(STATUS_MESSAGE))
              .count()).isEqualTo(1);
    });
  }

  private Deployment testDeployment() {
    Deployment resource = new Deployment();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("test-deployment")
            .build());
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
    container.setImage("nginx:1.14.2");
    ContainerPort port = new ContainerPort();
    port.setContainerPort(80);
    container.setPorts(List.of(port));

    podTemplate.getSpec().setContainers(List.of(container));
    return resource;
  }
}
