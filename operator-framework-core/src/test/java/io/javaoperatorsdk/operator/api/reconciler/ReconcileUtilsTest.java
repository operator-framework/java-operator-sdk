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
package io.javaoperatorsdk.operator.api.reconciler;

import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReconcileUtilsTest {

  private static final Logger log = LoggerFactory.getLogger(ReconcileUtilsTest.class);
  private static final String FINALIZER_NAME = "test.javaoperatorsdk.io/finalizer";

  private Context<TestCustomResource> context;
  private KubernetesClient client;
  private MixedOperation mixedOperation;
  private Resource resourceOp;
  private ControllerEventSource<TestCustomResource> controllerEventSource;
  private ControllerConfiguration<TestCustomResource> controllerConfiguration;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setupMocks() {
    context = mock(Context.class);
    client = mock(KubernetesClient.class);
    mixedOperation = mock(MixedOperation.class);
    resourceOp = mock(Resource.class);
    controllerEventSource = mock(ControllerEventSource.class);
    controllerConfiguration = mock(ControllerConfiguration.class);

    var eventSourceRetriever = mock(EventSourceRetriever.class);

    when(context.getClient()).thenReturn(client);
    when(context.eventSourceRetriever()).thenReturn(eventSourceRetriever);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
    when(controllerConfiguration.getFinalizerName()).thenReturn(FINALIZER_NAME);
    when(eventSourceRetriever.getControllerEventSource()).thenReturn(controllerEventSource);

    when(client.resources(TestCustomResource.class)).thenReturn(mixedOperation);
    when(mixedOperation.inNamespace(any())).thenReturn(mixedOperation);
    when(mixedOperation.withName(any())).thenReturn(resourceOp);
  }

  @Test
  void validateAndCompareResourceVersionsTest() {
    assertThat(ReconcileUtils.validateAndCompareResourceVersions("11", "22")).isNegative();
    assertThat(ReconcileUtils.validateAndCompareResourceVersions("22", "11")).isPositive();
    assertThat(ReconcileUtils.validateAndCompareResourceVersions("1", "1")).isZero();
    assertThat(ReconcileUtils.validateAndCompareResourceVersions("11", "11")).isZero();
    assertThat(ReconcileUtils.validateAndCompareResourceVersions("123", "2")).isPositive();
    assertThat(ReconcileUtils.validateAndCompareResourceVersions("3", "211")).isNegative();

    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcileUtils.validateAndCompareResourceVersions("aa", "22"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcileUtils.validateAndCompareResourceVersions("11", "ba"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcileUtils.validateAndCompareResourceVersions("", "22"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcileUtils.validateAndCompareResourceVersions("11", ""));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcileUtils.validateAndCompareResourceVersions("01", "123"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcileUtils.validateAndCompareResourceVersions("123", "01"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcileUtils.validateAndCompareResourceVersions("3213", "123a"));
    assertThrows(
        NonComparableResourceVersionException.class,
        () -> ReconcileUtils.validateAndCompareResourceVersions("321", "123a"));
  }

  @Test
  public void compareResourceVersionsWithStrings() {
    // Test equal versions
    assertThat(ReconcileUtils.compareResourceVersions("1", "1")).isZero();
    assertThat(ReconcileUtils.compareResourceVersions("123", "123")).isZero();

    // Test different lengths - shorter version is less than longer version
    assertThat(ReconcileUtils.compareResourceVersions("1", "12")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("12", "1")).isPositive();
    assertThat(ReconcileUtils.compareResourceVersions("99", "100")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("100", "99")).isPositive();
    assertThat(ReconcileUtils.compareResourceVersions("9", "100")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("100", "9")).isPositive();

    // Test same length - lexicographic comparison
    assertThat(ReconcileUtils.compareResourceVersions("1", "2")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("2", "1")).isPositive();
    assertThat(ReconcileUtils.compareResourceVersions("11", "12")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("12", "11")).isPositive();
    assertThat(ReconcileUtils.compareResourceVersions("99", "100")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("100", "99")).isPositive();
    assertThat(ReconcileUtils.compareResourceVersions("123", "124")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("124", "123")).isPositive();

    // Test with non-numeric strings (algorithm should still work character-wise)
    assertThat(ReconcileUtils.compareResourceVersions("a", "b")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("b", "a")).isPositive();
    assertThat(ReconcileUtils.compareResourceVersions("abc", "abd")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("abd", "abc")).isPositive();

    // Test edge cases with larger numbers
    assertThat(ReconcileUtils.compareResourceVersions("1234567890", "1234567891")).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions("1234567891", "1234567890")).isPositive();
  }

  @Test
  void compareResourceVersionsWithHasMetadata() {
    // Test equal versions
    HasMetadata resource1 = createResourceWithVersion("123");
    HasMetadata resource2 = createResourceWithVersion("123");
    assertThat(ReconcileUtils.compareResourceVersions(resource1, resource2)).isZero();

    // Test different lengths
    resource1 = createResourceWithVersion("1");
    resource2 = createResourceWithVersion("12");
    assertThat(ReconcileUtils.compareResourceVersions(resource1, resource2)).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions(resource2, resource1)).isPositive();

    // Test same length, different values
    resource1 = createResourceWithVersion("100");
    resource2 = createResourceWithVersion("200");
    assertThat(ReconcileUtils.compareResourceVersions(resource1, resource2)).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions(resource2, resource1)).isPositive();

    // Test realistic Kubernetes resource versions
    resource1 = createResourceWithVersion("12345");
    resource2 = createResourceWithVersion("12346");
    assertThat(ReconcileUtils.compareResourceVersions(resource1, resource2)).isNegative();
    assertThat(ReconcileUtils.compareResourceVersions(resource2, resource1)).isPositive();
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

  @Test
  void addsFinalizer() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");

    when(context.getPrimaryResource()).thenReturn(resource);

    // Mock successful finalizer addition
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("2");
              res.addFinalizer(FINALIZER_NAME);
              return res;
            });

    var result = ReconcileUtils.addFinalizer(context, FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isTrue();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("2");
    verify(controllerEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void addsFinalizerWithSSA() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");

    when(context.getPrimaryResource()).thenReturn(resource);

    // Mock successful SSA finalizer addition
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("2");
              res.addFinalizer(FINALIZER_NAME);
              return res;
            });

    var result = ReconcileUtils.addFinalizerWithSSA(context, FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isTrue();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("2");
    verify(controllerEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void removesFinalizer() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");
    resource.addFinalizer(FINALIZER_NAME);

    when(context.getPrimaryResource()).thenReturn(resource);

    // Mock successful finalizer removal
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("2");
              // finalizer is removed, so don't add it
              return res;
            });

    var result = ReconcileUtils.removeFinalizer(context, FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isFalse();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("2");
    verify(controllerEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
  }

  @Test
  void retriesAddingFinalizerWithoutSSA() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");

    when(context.getPrimaryResource()).thenReturn(resource);

    // First call throws conflict, second succeeds
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenThrow(new KubernetesClientException("Conflict", 409, null))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("2");
              res.addFinalizer(FINALIZER_NAME);
              return res;
            });

    // Return fresh resource on retry
    when(resourceOp.get()).thenReturn(resource);

    var result = ReconcileUtils.addFinalizer(context, FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isTrue();
    verify(controllerEventSource, times(2))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(resourceOp, times(1)).get();
  }

  @Test
  void nullResourceIsGracefullyHandledOnFinalizerRemovalRetry() {
    var resource = TestUtils.testCustomResource1();
    resource.getMetadata().setResourceVersion("1");
    resource.addFinalizer(FINALIZER_NAME);

    when(context.getPrimaryResource()).thenReturn(resource);

    // First call throws conflict
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenThrow(new KubernetesClientException("Conflict", 409, null));

    // Return null on retry (resource was deleted)
    when(resourceOp.get()).thenReturn(null);

    ReconcileUtils.removeFinalizer(context, FINALIZER_NAME);

    verify(controllerEventSource, times(1))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(resourceOp, times(1)).get();
  }

  @Test
  void retriesFinalizerRemovalWithFreshResource() {
    var originalResource = TestUtils.testCustomResource1();
    originalResource.getMetadata().setResourceVersion("1");
    originalResource.addFinalizer(FINALIZER_NAME);

    when(context.getPrimaryResource()).thenReturn(originalResource);

    // First call throws unprocessable (422), second succeeds
    when(controllerEventSource.eventFilteringUpdateAndCacheResource(
            any(), any(UnaryOperator.class)))
        .thenThrow(new KubernetesClientException("Unprocessable", 422, null))
        .thenAnswer(
            invocation -> {
              var res = TestUtils.testCustomResource1();
              res.getMetadata().setResourceVersion("3");
              // finalizer should be removed
              return res;
            });

    // Return fresh resource with newer version on retry
    var freshResource = TestUtils.testCustomResource1();
    freshResource.getMetadata().setResourceVersion("2");
    freshResource.addFinalizer(FINALIZER_NAME);
    when(resourceOp.get()).thenReturn(freshResource);

    var result = ReconcileUtils.removeFinalizer(context, FINALIZER_NAME);

    assertThat(result).isNotNull();
    assertThat(result.getMetadata().getResourceVersion()).isEqualTo("3");
    assertThat(result.hasFinalizer(FINALIZER_NAME)).isFalse();
    verify(controllerEventSource, times(2))
        .eventFilteringUpdateAndCacheResource(any(), any(UnaryOperator.class));
    verify(resourceOp, times(1)).get();
  }

  // naive performance test that compares the work case scenario for the parsing and non-parsing
  // variants
  @Test
  @Disabled
  public void compareResourcePerformanceTest() {
    var execNum = 30000000;
    var startTime = System.currentTimeMillis();
    for (int i = 0; i < execNum; i++) {
      var res = ReconcileUtils.compareResourceVersions("123456788" + i, "123456789" + i);
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
}
