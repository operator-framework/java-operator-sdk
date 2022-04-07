package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.sample.simple.TestCustomReconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.ReconcilerUtils.getDefaultFinalizerName;
import static io.javaoperatorsdk.operator.ReconcilerUtils.getDefaultNameFor;
import static io.javaoperatorsdk.operator.ReconcilerUtils.getDefaultReconcilerName;
import static io.javaoperatorsdk.operator.ReconcilerUtils.handleKubernetesClientException;
import static io.javaoperatorsdk.operator.ReconcilerUtils.isFinalizerValid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
  void handleKubernetesExceptionShouldThrowMissingCRDExceptionWhenAppropriate() {
    assertThrows(MissingCRDException.class, () -> handleKubernetesClientException(
        new KubernetesClientException(
            "Failure executing: GET at: https://kubernetes.docker.internal:6443/apis/tomcatoperator.io/v1/tomcats. Message: Not Found.",
            404, null),
        HasMetadata.getFullResourceName(Tomcat.class)));
  }

  @Group("tomcatoperator.io")
  @Version("v1")
  @ShortNames("tc")
  private static class Tomcat extends CustomResource<Void, Void> implements Namespaced {
  }

}
