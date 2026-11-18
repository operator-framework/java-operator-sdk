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

package io.javaoperatorsdk.operator.processing.event.source.polling;

import java.util.concurrent.ScheduledExecutorService;

import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;

final class PollingExecutors {

  private PollingExecutors() {}

  /**
   * Whether the operator the event source belongs to is configured to use virtual threads. The
   * controller configuration is {@code null} when the event source is used standalone, outside an
   * operator.
   */
  static boolean useVirtualThreads(EventSourceContext<?> context) {
    final var controllerConfiguration = context.getControllerConfiguration();
    if (controllerConfiguration == null) {
      return false;
    }
    final var configurationService = controllerConfiguration.getConfigurationService();
    return configurationService != null && configurationService.useVirtualThreads();
  }

  static ScheduledExecutorService newExecutor(String eventSourceName, boolean useVirtualThreads) {
    return ExecutorServiceManager.newScheduledExecutorService(
        1, useVirtualThreads, "josdk-polling-" + eventSourceName);
  }
}
