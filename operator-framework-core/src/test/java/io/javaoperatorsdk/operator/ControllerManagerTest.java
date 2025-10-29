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

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ResolvedControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.sample.simple.TestCustomReconciler;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.ControllerManager.CANNOT_REGISTER_MULTIPLE_CONTROLLERS_WITH_SAME_NAME_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerManagerTest {

  @Test
  void addingReconcilerWithSameNameShouldNotWork() {
    final var controllerConfiguration =
        new TestControllerConfiguration<>(new TestCustomReconciler(null), TestCustomResource.class);
    var controller =
        new Controller<>(
            controllerConfiguration.reconciler,
            controllerConfiguration,
            MockKubernetesClient.client(controllerConfiguration.getResourceClass()));
    ConfigurationService configurationService = new BaseConfigurationService();
    final var controllerManager =
        new ControllerManager(configurationService.getExecutorServiceManager());
    controllerManager.add(controller);

    var ex =
        assertThrows(
            OperatorException.class,
            () -> {
              controllerManager.add(controller);
            });
    assertTrue(
        ex.getMessage().contains(CANNOT_REGISTER_MULTIPLE_CONTROLLERS_WITH_SAME_NAME_MESSAGE));
  }

  private static class TestControllerConfiguration<R extends HasMetadata>
      extends ResolvedControllerConfiguration<R> {
    private final Reconciler<R> reconciler;

    public TestControllerConfiguration(Reconciler<R> reconciler, Class<R> crClass) {
      super(
          crClass,
          getControllerName(reconciler),
          reconciler.getClass(),
          new BaseConfigurationService());
      this.reconciler = reconciler;
    }

    static <R extends HasMetadata> String getControllerName(Reconciler<R> controller) {
      return controller.getClass().getSimpleName() + "Controller";
    }
  }
}
