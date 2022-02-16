package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class DesiredValueMatcherTest {

  DesiredValueMatcher desiredValueMatcher = new DesiredValueMatcher(new ObjectMapper());

  @Test
  void checksIfDesiredValuesAreTheSame() {
    var target1 = createTestDeploymentObject();
    var desired1 = createTestDeploymentObject();
    assertThat(desiredValueMatcher.match(target1, desired1, null)).isTrue();

    var target2 = createTestDeploymentObject();
    var desired2 = createTestDeploymentObject();
    target2.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(desiredValueMatcher.match(target2, desired2, null)).isTrue()
        .withFailMessage("Additive changes should be ok");

    var target3 = createTestDeploymentObject();
    var desired3 = createTestDeploymentObject();
    desired3.getSpec().getTemplate().getMetadata().getLabels().put("new-key", "val");
    assertThat(desiredValueMatcher.match(target3, desired3, null)).isFalse()
        .withFailMessage("Removed value should not be ok");

    var target4 = createTestDeploymentObject();
    var desired4 = createTestDeploymentObject();
    target4.getSpec().setReplicas(2);
    assertThat(desiredValueMatcher.match(target4, desired4, null)).isFalse()
        .withFailMessage("Changed values are not ok");

  }

  Deployment createTestDeploymentObject() {
    Deployment deployment =
        ReconcilerUtils.loadYaml(Deployment.class, DesiredValueMatcherTest.class,
            "nginx-deployment.yaml");
    deployment.getMetadata().setName("test");
    return deployment;
  }

}
