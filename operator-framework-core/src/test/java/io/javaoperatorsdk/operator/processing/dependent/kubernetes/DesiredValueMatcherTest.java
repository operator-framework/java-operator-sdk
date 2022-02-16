package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class DesiredValueMatcherTest {

  DesiredValueMatcher desiredValueMatcher = new DesiredValueMatcher(new ObjectMapper());

  @Test
  void checksIfDesiredValuesAreTheSame() {
    var target1 = createDeployment();
    var desired1 = createDeployment();
    assertThat(desiredValueMatcher.match(target1, desired1, null)).isTrue();

    var target2 = createDeployment();
    var desired2 = createDeployment();
    target2.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(desiredValueMatcher.match(target2, desired2, null))
        .withFailMessage("Additive changes should be ok")
        .isTrue();

    var target3 = createDeployment();
    var desired3 = createDeployment();
    desired3.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(desiredValueMatcher.match(target3, desired3, null))
        .withFailMessage("Removed value should not be ok")
        .isFalse();

    var target4 = createDeployment();
    var desired4 = createDeployment();
    target4.getSpec().setReplicas(2);
    assertThat(desiredValueMatcher.match(target4, desired4, null))
        .withFailMessage("Changed values are not ok")
        .isFalse();
  }

  Deployment createDeployment() {
    Deployment deployment =
        ReconcilerUtils.loadYaml(
            Deployment.class, DesiredValueMatcherTest.class, "nginx-deployment.yaml");
    return deployment;
  }
}
