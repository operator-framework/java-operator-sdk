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

import java.util.Optional;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils.DEFAULT_MAX_RETRY;
import static io.javaoperatorsdk.operator.api.reconciler.PrimaryUpdateAndCacheUtils.compareResourceVersions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrimaryUpdateAndCacheUtilsTest {

  private static final Logger log = LoggerFactory.getLogger(PrimaryUpdateAndCacheUtilsTest.class);

  Context<TestCustomResource> context = mock(Context.class);
  KubernetesClient client = mock(KubernetesClient.class);
  Resource resource = mock(Resource.class);
  IndexedResourceCache<TestCustomResource> primaryCache = mock(IndexedResourceCache.class);

  @BeforeEach
  void setup() {
    when(context.getClient()).thenReturn(client);
    var esr = mock(EventSourceRetriever.class);
    when(context.eventSourceRetriever()).thenReturn(esr);
    when(esr.getControllerEventSource()).thenReturn(mock(ControllerEventSource.class));
    var mixedOp = mock(MixedOperation.class);
    when(client.resources(any())).thenReturn(mixedOp);
    when(mixedOp.inNamespace(any())).thenReturn(mixedOp);
    when(mixedOp.withName(any())).thenReturn(resource);
    when(resource.get()).thenReturn(TestUtils.testCustomResource1());
    when(context.getPrimaryCache()).thenReturn(primaryCache);

    var controllerConfiguration = mock(ControllerConfiguration.class);
    when(context.getControllerConfiguration()).thenReturn(controllerConfiguration);
    var configService = mock(ConfigurationService.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configService);
    when(configService.getResourceCloner())
        .thenReturn(
            new Cloner() {
              @Override
              public <R extends HasMetadata> R clone(R object) {
                return new KubernetesSerialization().clone(object);
              }
            });
  }

  @Test
  void handlesUpdate() {
    var updated =
        PrimaryUpdateAndCacheUtils.updateAndCacheResource(
            TestUtils.testCustomResource1(),
            context,
            r -> {
              var res = TestUtils.testCustomResource1();
              // setting this to null to test if value set in the implementation
              res.getMetadata().setResourceVersion(null);
              res.getSpec().setValue("updatedValue");
              return res;
            },
            r -> {
              // checks if the resource version is set from the original resource
              assertThat(r.getMetadata().getResourceVersion()).isEqualTo("1");
              var res = TestUtils.testCustomResource1();
              res.setSpec(r.getSpec());
              res.getMetadata().setResourceVersion("2");
              return res;
            });

    assertThat(updated.getMetadata().getResourceVersion()).isEqualTo("2");
    assertThat(updated.getSpec().getValue()).isEqualTo("updatedValue");
  }

  @Test
  void retriesConflicts() {
    var updateOperation = mock(UnaryOperator.class);

    when(updateOperation.apply(any()))
        .thenThrow(new KubernetesClientException("", 409, null))
        .thenReturn(TestUtils.testCustomResource1());
    var freshResource = TestUtils.testCustomResource1();

    freshResource.getMetadata().setResourceVersion("2");
    when(primaryCache.get(any())).thenReturn(Optional.of(freshResource));

    var updated =
        PrimaryUpdateAndCacheUtils.updateAndCacheResource(
            TestUtils.testCustomResource1(),
            context,
            r -> {
              var res = TestUtils.testCustomResource1();
              res.getSpec().setValue("updatedValue");
              return res;
            },
            updateOperation);

    assertThat(updated).isNotNull();
    verify(primaryCache, times(1)).get(any());
  }

  @Test
  void throwsIfRetryExhausted() {
    var updateOperation = mock(UnaryOperator.class);

    when(updateOperation.apply(any())).thenThrow(new KubernetesClientException("", 409, null));
    var stubbing = when(primaryCache.get(any()));

    for (int i = 0; i < DEFAULT_MAX_RETRY; i++) {
      var resource = TestUtils.testCustomResource1();
      resource.getMetadata().setResourceVersion("" + i);
      stubbing = stubbing.thenReturn(Optional.of(resource));
    }
    assertThrows(
        OperatorException.class,
        () ->
            PrimaryUpdateAndCacheUtils.updateAndCacheResource(
                TestUtils.testCustomResource1(),
                context,
                UnaryOperator.identity(),
                updateOperation));
    verify(primaryCache, times(DEFAULT_MAX_RETRY)).get(any());
  }

  @Test
  void cachePollTimeouts() {
    var updateOperation = mock(UnaryOperator.class);

    when(updateOperation.apply(any())).thenThrow(new KubernetesClientException("", 409, null));
    when(primaryCache.get(any())).thenReturn(Optional.of(TestUtils.testCustomResource1()));

    var ex =
        assertThrows(
            OperatorException.class,
            () ->
                PrimaryUpdateAndCacheUtils.updateAndCacheResource(
                    TestUtils.testCustomResource1(),
                    context,
                    UnaryOperator.identity(),
                    updateOperation,
                    2,
                    50L,
                    10L));
    assertThat(ex.getMessage()).contains("Timeout");
  }

  @Test
  public void compareResourceVersionsTest() {
    assertThat(compareResourceVersions("11", "22")).isNegative();
    assertThat(compareResourceVersions("22", "11")).isPositive();
    assertThat(compareResourceVersions("11", "11")).isZero();

    assertThat(compareResourceVersions("123", "2")).isPositive();
    assertThat(compareResourceVersions("3", "211")).isNegative();

    assertThrows(IllegalArgumentException.class, () -> compareResourceVersions("aa", "22"));
    assertThrows(IllegalArgumentException.class, () -> compareResourceVersions("11", "ba"));
    assertThrows(IllegalArgumentException.class, () -> compareResourceVersions("", "22"));
    assertThrows(IllegalArgumentException.class, () -> compareResourceVersions("11", ""));
  }

  // naive performance that compares the works case scenario for non parsing variant
  @Test
  public void compareResourcePerformanceTest() {
    var execNum = 20000000;
    var startTime = System.currentTimeMillis();
    for (int i = 0; i < execNum; i++) {
      var res = compareResourceVersions("123456788", "123456789");
    }
    var dur1 = System.currentTimeMillis() - startTime;
    log.info("Duration without parsing: {}", dur1);
    startTime = System.currentTimeMillis();
    for (int i = 0; i < execNum; i++) {
      var res = Long.parseLong("123456788") > Long.parseLong("123456789");
    }
    var dur2 = System.currentTimeMillis() - startTime;
    log.info("Duration with parsing:   {}", dur2);

    assertThat(dur1).isLessThan(dur2);
  }
}
