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
package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ExecutorServiceManagerTest {

  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofMillis(100);

  @Test
  void stopShutsDownTheScheduledExecutorService() {
    ConfigurationService configurationService = new BaseConfigurationService();
    var manager = configurationService.getExecutorServiceManager();
    var scheduled = manager.scheduledExecutorService();
    var retryAndReschedule = manager.retryAndRescheduleExecutorService();

    try {
      assertThat(scheduled.isShutdown()).isFalse();
      assertThat(retryAndReschedule.isShutdown()).isFalse();
      // retries are triggered on an executor of their own so that a slow poll can't delay them
      assertThat(retryAndReschedule).isNotSameAs(scheduled);
    } finally {
      manager.stop(SHUTDOWN_TIMEOUT);
    }

    assertThat(scheduled.isShutdown()).isTrue();
    assertThat(retryAndReschedule.isShutdown()).isTrue();
  }

  @Test
  void canBeRestartedAfterStop() {
    ConfigurationService configurationService = new BaseConfigurationService();
    var manager = configurationService.getExecutorServiceManager();

    manager.stop(SHUTDOWN_TIMEOUT);
    manager.start(configurationService);

    // start() is a no-op unless stop() reset the started flag, which would leave the manager
    // handing out already terminated executors
    assertThat(manager.reconcileExecutorService().isShutdown()).isFalse();
    assertThat(manager.cachingExecutorService().isShutdown()).isFalse();
    assertThat(manager.scheduledExecutorService().isShutdown()).isFalse();
    assertThat(manager.retryAndRescheduleExecutorService().isShutdown()).isFalse();

    manager.stop(SHUTDOWN_TIMEOUT);
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void cachingExecutorRunsOnVirtualThreadsWhenRequested() throws Exception {
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(), o -> o.withUseVirtualThreads(true));
    var manager = configurationService.getExecutorServiceManager();
    try {
      var virtual =
          manager.cachingExecutorService().submit(() -> VirtualThreadsTest.onVirtualThread()).get();
      assertThat(virtual).isTrue();
    } finally {
      manager.stop(SHUTDOWN_TIMEOUT);
    }
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void keepAliveThreadRunsOnlyWhileTheManagerIsStartedWithVirtualThreads() throws Exception {
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            new BaseConfigurationService(), o -> o.withUseVirtualThreads(true));
    var manager = configurationService.getExecutorServiceManager();

    assertThat(keepAliveThread()).hasValueSatisfying(t -> assertThat(t.isDaemon()).isFalse());

    manager.stop(SHUTDOWN_TIMEOUT);

    await().untilAsserted(() -> assertThat(keepAliveThread()).isEmpty());
  }

  @Test
  void noKeepAliveThreadWithPlatformThreads() {
    var manager = new BaseConfigurationService().getExecutorServiceManager();
    try {
      assertThat(keepAliveThread()).isEmpty();
    } finally {
      manager.stop(SHUTDOWN_TIMEOUT);
    }
  }

  private static Optional<Thread> keepAliveThread() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.getName().equals("josdk-keep-alive") && t.isAlive())
        .findFirst();
  }
}
