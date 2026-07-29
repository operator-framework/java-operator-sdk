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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorServiceManagerTest {

  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofMillis(100);

  @Test
  void stopShutsDownTheScheduledExecutorService() {
    ConfigurationService configurationService = new BaseConfigurationService();
    var manager = configurationService.getExecutorServiceManager();
    var scheduled = manager.scheduledExecutorService();
    assertThat(scheduled.isShutdown()).isFalse();

    manager.stop(SHUTDOWN_TIMEOUT);

    assertThat(scheduled.isShutdown()).isTrue();
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

    manager.stop(SHUTDOWN_TIMEOUT);
  }
}
