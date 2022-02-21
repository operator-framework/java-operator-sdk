package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceUpdatePreProcessorTest {

  ResourceUpdatePreProcessor<Deployment> resourceUpdatePreProcessor =
      new ResourceUpdatePreProcessor<>(ConfigurationService.DEFAULT_CLONER);

  @Test
  void preservesValues() {
    var desired = createDeployment();
    var actual = createDeployment();
    actual.getMetadata().setLabels(new HashMap<>());
    actual.getMetadata().getLabels().put("additionalActualKey", "value");
    actual.getMetadata().setResourceVersion("1234");
    actual.getSpec().setRevisionHistoryLimit(5);

    var result = resourceUpdatePreProcessor.replaceSpecOnActual(actual, desired);

    assertThat(result.getMetadata().getLabels().get("additionalActualKey")).isEqualTo("value");
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("1234");
    assertThat(result.getSpec().getRevisionHistoryLimit()).isEqualTo(10);
  }

  Deployment createDeployment() {
    Deployment deployment =
        ReconcilerUtils.loadYaml(
            Deployment.class, ResourceUpdatePreProcessorTest.class, "nginx-deployment.yaml");
    return deployment;
  }

}
