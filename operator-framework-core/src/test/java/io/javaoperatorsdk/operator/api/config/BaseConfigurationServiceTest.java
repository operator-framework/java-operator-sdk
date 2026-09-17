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

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.assertj.core.api.Assertions.assertThat;

class BaseConfigurationServiceTest {

  private final BaseConfigurationService configurationService = new BaseConfigurationService();

  @Test
  void readsTriggerReconcilerOnAllEvents() {
    assertThat(
            configurationService
                .configFor(new AllEventsReconciler())
                .triggerReconcilerOnAllEvents())
        .isTrue();
  }

  @Test
  void readsDeprecatedTriggerReconcilerOnAllEventAttribute() {
    assertThat(
            configurationService
                .configFor(new DeprecatedAllEventsReconciler())
                .triggerReconcilerOnAllEvents())
        .isTrue();
  }

  @Test
  void triggerReconcilerOnAllEventsDefaultsToFalse() {
    assertThat(
            configurationService.configFor(new DefaultReconciler()).triggerReconcilerOnAllEvents())
        .isFalse();
  }

  @ControllerConfiguration(triggerReconcilerOnAllEvents = true)
  private static class AllEventsReconciler implements Reconciler<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return UpdateControl.noUpdate();
    }
  }

  @SuppressWarnings("removal")
  @ControllerConfiguration(triggerReconcilerOnAllEvent = true)
  private static class DeprecatedAllEventsReconciler implements Reconciler<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return UpdateControl.noUpdate();
    }
  }

  @ControllerConfiguration
  private static class DefaultReconciler implements Reconciler<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return UpdateControl.noUpdate();
    }
  }
}
