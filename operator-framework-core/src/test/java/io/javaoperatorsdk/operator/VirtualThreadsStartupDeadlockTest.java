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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * With virtual threads enabled on Java 21 to 24 (no JEP 491, hence only run on Java 25 or later,
 * the official baseline for virtual thread support), starting as many controllers as there are
 * virtual thread carriers deadlocks, because each controller is started on a virtual thread of the
 * caching executor that blocks, while holding the monitors of the synchronized Controller.start /
 * EventSourceManager.start / ControllerEventSource.start, on informer-start tasks submitted to the
 * very same virtual thread executor.
 */
class VirtualThreadsStartupDeadlockTest {

  @Test
  @EnabledForJreRange(min = JRE.JAVA_25)
  @SuppressWarnings({"rawtypes", "unchecked"})
  void startingControllersOnVirtualThreadsDoesNotDeadlock() throws Exception {
    assertStartsWithoutDeadlock(true);
  }

  @Test
  void controlStartingControllersOnPlatformThreads() throws Exception {
    assertStartsWithoutDeadlock(false);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void assertStartsWithoutDeadlock(boolean useVirtualThreads) throws Exception {
    final var mockClient = MockKubernetesClient.client(ConfigMap.class);
    final ConfigurationService configService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(),
            o -> o.withKubernetesClient(mockClient).withUseVirtualThreads(useVirtualThreads));
    final var executorServiceManager = configService.getExecutorServiceManager();
    final var controllerManager = new ControllerManager(executorServiceManager);

    // default virtual thread scheduler parallelism == number of available processors, e.g. 1 or 2
    // in a typical resource-limited operator pod
    final int controllers =
        System.getenv("AUDIT_N") != null
            ? Integer.parseInt(System.getenv("AUDIT_N"))
            : Runtime.getRuntime().availableProcessors();
    for (int i = 0; i < controllers; i++) {
      final var configuration = MockControllerConfiguration.forResource(ConfigMap.class);
      when(configuration.getConfigurationService()).thenReturn(configService);
      when(configuration.getName()).thenReturn("controller-" + i);
      controllerManager.add(new Controller(mock(Reconciler.class), configuration, mockClient));
    }

    final var start = CompletableFuture.runAsync(() -> controllerManager.start(false));
    try {
      start.get(20, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      // break the deadlock (interrupts the pinned virtual threads) so that the shared virtual
      // thread scheduler isn't left starved for the rest of the test JVM
      executorServiceManager.stop(Duration.ZERO);
      try {
        start.get(20, TimeUnit.SECONDS);
      } catch (Exception ignored) {
        // expected once the stuck start was interrupted
      }
      assertThat(false)
          .as(
              "ControllerManager.start deadlocked starting %d controllers on virtual threads",
              controllers)
          .isTrue();
    }
    executorServiceManager.stop(Duration.ZERO);
  }
}
