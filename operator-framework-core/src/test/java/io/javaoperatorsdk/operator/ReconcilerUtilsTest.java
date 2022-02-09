package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.sample.simple.TestCustomReconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.ReconcilerUtils.getDefaultFinalizerName;
import static io.javaoperatorsdk.operator.ReconcilerUtils.getDefaultNameFor;
import static io.javaoperatorsdk.operator.ReconcilerUtils.getDefaultReconcilerName;
import static io.javaoperatorsdk.operator.ReconcilerUtils.isFinalizerValid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconcilerUtilsTest {

  @Test
  void defaultReconcilerNameShouldWork() {
    assertEquals(
        "testcustomreconciler",
        getDefaultReconcilerName(TestCustomReconciler.class.getCanonicalName()));
    assertEquals(
        getDefaultNameFor(TestCustomReconciler.class),
        getDefaultReconcilerName(TestCustomReconciler.class.getCanonicalName()));
    assertEquals(
        getDefaultNameFor(TestCustomReconciler.class),
        getDefaultReconcilerName(TestCustomReconciler.class.getSimpleName()));
  }

  @Test
  void defaultFinalizerShouldWork() {
    assertTrue(isFinalizerValid(getDefaultFinalizerName(Pod.class)));
    assertTrue(isFinalizerValid(getDefaultFinalizerName(TestCustomResource.class)));
  }

  @Test
  void noFinalizerMarkerShouldWork() {
    assertTrue(isFinalizerValid(Constants.NO_FINALIZER));
  }


  @Test
  void comparesArbitrarySpecsOfObjects() {
    var d1 = createTestDeployment();
    var d2 = createTestDeployment();

    assertThat(ReconcilerUtils.specsSame(d1, d2)).isTrue();
  }

  @Test
  void comparesArbitraryDifferentSpecsOfObjects() {
    var d1 = createTestDeployment();
    var d2 = createTestDeployment();
    d2.getSpec().getTemplate().getSpec().setHostname("otherhost");

    assertThat(ReconcilerUtils.specsSame(d1, d2)).isFalse();
  }

  @Test
  void equalsSpecObject() {
    var d1 = createTestDeployment();
    var d2 = createTestDeployment();

    assertThat(ReconcilerUtils.specsEqual(d1, d2)).isTrue();
  }

  @Test
  void equalArbitraryDifferentSpecsOfObjects() {
    var d1 = createTestDeployment();
    var d2 = createTestDeployment();
    d2.getSpec().getTemplate().getSpec().setHostname("otherhost");

    assertThat(ReconcilerUtils.specsEqual(d1, d2)).isFalse();
  }

  @Test
  void getsSpecWithReflection() {
    Deployment deployment = new Deployment();
    deployment.setSpec(new DeploymentSpec());
    deployment.getSpec().setReplicas(5);

    DeploymentSpec spec = (DeploymentSpec) ReconcilerUtils.getSpec(deployment);
    assertThat(spec.getReplicas()).isEqualTo(5);
  }

  private Deployment createTestDeployment() {
    Deployment deployment = new Deployment();
    deployment.setSpec(new DeploymentSpec());
    deployment.getSpec().setReplicas(5);
    PodTemplateSpec podTemplateSpec = new PodTemplateSpec();
    deployment.getSpec().setTemplate(podTemplateSpec);
    podTemplateSpec.setSpec(new PodSpec());
    podTemplateSpec.getSpec().setHostname("localhost");
    return deployment;
  }
}
