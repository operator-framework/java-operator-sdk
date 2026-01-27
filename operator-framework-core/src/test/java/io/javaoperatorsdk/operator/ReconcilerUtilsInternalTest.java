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
package io.javaoperatorsdk.operator;

import java.net.URI;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.http.HttpRequest;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.api.reconciler.NonComparableResourceVersionException;
import io.javaoperatorsdk.operator.sample.simple.TestCustomReconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.ReconcilerUtilsInternal.getDefaultFinalizerName;
import static io.javaoperatorsdk.operator.ReconcilerUtilsInternal.getDefaultNameFor;
import static io.javaoperatorsdk.operator.ReconcilerUtilsInternal.getDefaultReconcilerName;
import static io.javaoperatorsdk.operator.ReconcilerUtilsInternal.handleKubernetesClientException;
import static io.javaoperatorsdk.operator.ReconcilerUtilsInternal.isFinalizerValid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconcilerUtilsInternalTest {
  private static final Logger log = LoggerFactory.getLogger(ReconcilerUtilsInternalTest.class);
  public static final String RESOURCE_URI =
      "https://kubernetes.docker.internal:6443/apis/tomcatoperator.io/v1/tomcats";

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
  void equalsSpecObject() {
    var d1 = createTestDeployment();
    var d2 = createTestDeployment();

    assertThat(ReconcilerUtilsInternal.specsEqual(d1, d2)).isTrue();
  }

  @Test
  void equalArbitraryDifferentSpecsOfObjects() {
    var d1 = createTestDeployment();
    var d2 = createTestDeployment();
    d2.getSpec().getTemplate().getSpec().setHostname("otherhost");

    assertThat(ReconcilerUtilsInternal.specsEqual(d1, d2)).isFalse();
  }

  @Test
  void getsSpecWithReflection() {
    Deployment deployment = new Deployment();
    deployment.setSpec(new DeploymentSpec());
    deployment.getSpec().setReplicas(5);

    DeploymentSpec spec = (DeploymentSpec) ReconcilerUtilsInternal.getSpec(deployment);
    assertThat(spec.getReplicas()).isEqualTo(5);
  }

  @Test
  void properlyHandlesNullSpec() {
    Namespace ns = new Namespace();

    final var spec = ReconcilerUtilsInternal.getSpec(ns);
    assertThat(spec).isNull();

    ReconcilerUtilsInternal.setSpec(ns, null);
  }

  @Test
  void setsSpecWithReflection() {
    Deployment deployment = new Deployment();
    deployment.setSpec(new DeploymentSpec());
    deployment.getSpec().setReplicas(5);
    DeploymentSpec newSpec = new DeploymentSpec();
    newSpec.setReplicas(1);

    ReconcilerUtilsInternal.setSpec(deployment, newSpec);

    assertThat(deployment.getSpec().getReplicas()).isEqualTo(1);
  }

  @Test
  void setsSpecCustomResourceWithReflection() {
    Tomcat tomcat = new Tomcat();
    tomcat.setSpec(new TomcatSpec());
    tomcat.getSpec().setReplicas(5);
    TomcatSpec newSpec = new TomcatSpec();
    newSpec.setReplicas(1);

    ReconcilerUtilsInternal.setSpec(tomcat, newSpec);

    assertThat(tomcat.getSpec().getReplicas()).isEqualTo(1);
  }

  @Test
  void loadYamlAsBuilder() {
    DeploymentBuilder builder =
        ReconcilerUtilsInternal.loadYaml(DeploymentBuilder.class, getClass(), "deployment.yaml");
    builder.accept(ContainerBuilder.class, c -> c.withImage("my-image"));

    Deployment deployment = builder.editMetadata().withName("my-deployment").and().build();
    assertThat(deployment.getMetadata().getName()).isEqualTo("my-deployment");
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

  @Test
  void handleKubernetesExceptionShouldThrowMissingCRDExceptionWhenAppropriate() {
    var request = mock(HttpRequest.class);
    when(request.uri()).thenReturn(URI.create(RESOURCE_URI));
    assertThrows(
        MissingCRDException.class,
        () ->
            handleKubernetesClientException(
                new KubernetesClientException(
                    "Failure executing: GET at: " + RESOURCE_URI + ". Message: Not Found.",
                    null,
                    404,
                    null,
                    request),
                HasMetadata.getFullResourceName(Tomcat.class)));
  }

  @Group("tomcatoperator.io")
  @Version("v1")
  @ShortNames("tc")
  private static class Tomcat extends CustomResource<TomcatSpec, Void> implements Namespaced {}

  private static class TomcatSpec {
    private Integer replicas;

    public Integer getReplicas() {
      return replicas;
    }

    public void setReplicas(Integer replicas) {
      this.replicas = replicas;
    }
  }

  // naive performance test that compares the work case scenario for the parsing and non-parsing
  // variants
  @Test
  @Disabled
  public void compareResourcePerformanceTest() {
    var execNum = 30000000;
    var startTime = System.currentTimeMillis();
    for (int i = 0; i < execNum; i++) {
      var res = ReconcilerUtilsInternal.compareResourceVersions("123456788" + i, "123456789" + i);
    }
    var dur1 = System.currentTimeMillis() - startTime;
    log.info("Duration without parsing: {}", dur1);
    startTime = System.currentTimeMillis();
    for (int i = 0; i < execNum; i++) {
      var res = Long.parseLong("123456788" + i) > Long.parseLong("123456789" + i);
    }
    var dur2 = System.currentTimeMillis() - startTime;
    log.info("Duration with parsing:   {}", dur2);

    assertThat(dur1).isLessThan(dur2);
  }

  @Test
  void validateAndCompareResourceVersionsTest() {
    assertThat(ReconcilerUtilsInternal.validateAndCompareResourceVersions("11", "22")).isNegative();
    assertThat(ReconcilerUtilsInternal.validateAndCompareResourceVersions("22", "11")).isPositive();
    assertThat(ReconcilerUtilsInternal.validateAndCompareResourceVersions("1", "1")).isZero();
    assertThat(ReconcilerUtilsInternal.validateAndCompareResourceVersions("11", "11")).isZero();
    assertThat(ReconcilerUtilsInternal.validateAndCompareResourceVersions("123", "2")).isPositive();
    assertThat(ReconcilerUtilsInternal.validateAndCompareResourceVersions("3", "211")).isNegative();

    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcilerUtilsInternal.validateAndCompareResourceVersions("aa", "22"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcilerUtilsInternal.validateAndCompareResourceVersions("11", "ba"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcilerUtilsInternal.validateAndCompareResourceVersions("", "22"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcilerUtilsInternal.validateAndCompareResourceVersions("11", ""));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcilerUtilsInternal.validateAndCompareResourceVersions("01", "123"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcilerUtilsInternal.validateAndCompareResourceVersions("123", "01"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcilerUtilsInternal.validateAndCompareResourceVersions("3213", "123a"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcilerUtilsInternal.validateAndCompareResourceVersions("321", "123a"));
  }

  @Test
  void compareResourceVersionsWithStrings() {
    // Test equal versions
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("1", "1")).isZero();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("123", "123")).isZero();

    // Test different lengths - shorter version is less than longer version
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("1", "12")).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("12", "1")).isPositive();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("99", "100")).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("100", "99")).isPositive();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("9", "100")).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("100", "9")).isPositive();

    // Test same length - lexicographic comparison
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("1", "2")).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("2", "1")).isPositive();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("11", "12")).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("12", "11")).isPositive();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("99", "100")).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("100", "99")).isPositive();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("123", "124")).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("124", "123")).isPositive();

    // Test with non-numeric strings (algorithm should still work character-wise)
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("a", "b")).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("b", "a")).isPositive();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("abc", "abd")).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("abd", "abc")).isPositive();

    // Test edge cases with larger numbers
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("1234567890", "1234567891"))
        .isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions("1234567891", "1234567890"))
        .isPositive();
  }

  @Test
  void compareResourceVersionsWithHasMetadata() {
    // Test equal versions
    HasMetadata resource1 = createResourceWithVersion("123");
    HasMetadata resource2 = createResourceWithVersion("123");
    assertThat(ReconcilerUtilsInternal.compareResourceVersions(resource1, resource2)).isZero();

    // Test different lengths
    resource1 = createResourceWithVersion("1");
    resource2 = createResourceWithVersion("12");
    assertThat(ReconcilerUtilsInternal.compareResourceVersions(resource1, resource2)).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions(resource2, resource1)).isPositive();

    // Test same length, different values
    resource1 = createResourceWithVersion("100");
    resource2 = createResourceWithVersion("200");
    assertThat(ReconcilerUtilsInternal.compareResourceVersions(resource1, resource2)).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions(resource2, resource1)).isPositive();

    // Test realistic Kubernetes resource versions
    resource1 = createResourceWithVersion("12345");
    resource2 = createResourceWithVersion("12346");
    assertThat(ReconcilerUtilsInternal.compareResourceVersions(resource1, resource2)).isNegative();
    assertThat(ReconcilerUtilsInternal.compareResourceVersions(resource2, resource1)).isPositive();
  }

  private HasMetadata createResourceWithVersion(String resourceVersion) {
    return new PodBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName("test-pod")
                .withNamespace("default")
                .withResourceVersion(resourceVersion)
                .build())
        .build();
  }
}
