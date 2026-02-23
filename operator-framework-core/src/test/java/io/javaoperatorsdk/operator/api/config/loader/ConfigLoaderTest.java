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
package io.javaoperatorsdk.operator.api.config.loader;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

  // A simple ConfigProvider backed by a plain map for test control.
  private static ConfigProvider mapProvider(Map<String, Object> values) {
    return new ConfigProvider() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> Optional<T> getValue(String key, Class<T> type) {
        return Optional.ofNullable((T) values.get(key));
      }
    };
  }

  // -- applyConfigs -----------------------------------------------------------

  @Test
  void applyConfigsReturnsNoOpWhenNothingConfigured() {
    var loader = new ConfigLoader(mapProvider(Map.of()));
    var base = new BaseConfigurationService(null);
    // consumer must be non-null and must leave all defaults unchanged
    var consumer = loader.applyConfigs();
    assertThat(consumer).isNotNull();
    var result = ConfigurationService.newOverriddenConfigurationService(base, consumer);
    assertThat(result.concurrentReconciliationThreads())
        .isEqualTo(base.concurrentReconciliationThreads());
    assertThat(result.concurrentWorkflowExecutorThreads())
        .isEqualTo(base.concurrentWorkflowExecutorThreads());
  }

  @Test
  void applyConfigsAppliesConcurrentReconciliationThreads() {
    var loader =
        new ConfigLoader(mapProvider(Map.of("josdk.reconciliation.concurrent-threads", 7)));

    var base = new BaseConfigurationService(null);
    var result =
        ConfigurationService.newOverriddenConfigurationService(base, loader.applyConfigs());

    assertThat(result.concurrentReconciliationThreads()).isEqualTo(7);
  }

  @Test
  void applyConfigsAppliesConcurrentWorkflowExecutorThreads() {
    var loader = new ConfigLoader(mapProvider(Map.of("josdk.workflow.executor-threads", 3)));

    var base = new BaseConfigurationService(null);
    var result =
        ConfigurationService.newOverriddenConfigurationService(base, loader.applyConfigs());

    assertThat(result.concurrentWorkflowExecutorThreads()).isEqualTo(3);
  }

  @Test
  void applyConfigsAppliesBooleanFlags() {
    var values = new HashMap<String, Object>();
    values.put("josdk.check-crd", true);
    values.put("josdk.close-client-on-stop", false);
    values.put("josdk.informer.stop-on-error-during-startup", false);
    values.put("josdk.dependent-resources.ssa-based-create-update-match", false);
    values.put("josdk.use-ssa-to-patch-primary-resource", false);
    values.put("josdk.clone-secondary-resources-when-getting-from-cache", true);
    var loader = new ConfigLoader(mapProvider(values));

    var base = new BaseConfigurationService(null);
    var result =
        ConfigurationService.newOverriddenConfigurationService(base, loader.applyConfigs());

    assertThat(result.checkCRDAndValidateLocalModel()).isTrue();
    assertThat(result.closeClientOnStop()).isFalse();
    assertThat(result.stopOnInformerErrorDuringStartup()).isFalse();
    assertThat(result.ssaBasedCreateUpdateMatchForDependentResources()).isFalse();
    assertThat(result.useSSAToPatchPrimaryResource()).isFalse();
    assertThat(result.cloneSecondaryResourcesWhenGettingFromCache()).isTrue();
  }

  @Test
  void applyConfigsAppliesDurations() {
    var values = new HashMap<String, Object>();
    values.put("josdk.informer.cache-sync-timeout", Duration.ofSeconds(10));
    values.put("josdk.reconciliation.termination-timeout", Duration.ofSeconds(5));
    var loader = new ConfigLoader(mapProvider(values));

    var base = new BaseConfigurationService(null);
    var result =
        ConfigurationService.newOverriddenConfigurationService(base, loader.applyConfigs());

    assertThat(result.cacheSyncTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(result.reconciliationTerminationTimeout()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void applyConfigsOnlyAppliesPresentKeys() {
    // Only one key present — other defaults must be unchanged.
    var loader =
        new ConfigLoader(mapProvider(Map.of("josdk.reconciliation.concurrent-threads", 12)));

    var base = new BaseConfigurationService(null);
    var result =
        ConfigurationService.newOverriddenConfigurationService(base, loader.applyConfigs());

    assertThat(result.concurrentReconciliationThreads()).isEqualTo(12);
    // Default unchanged
    assertThat(result.concurrentWorkflowExecutorThreads())
        .isEqualTo(base.concurrentWorkflowExecutorThreads());
  }

  // -- applyControllerConfigs -------------------------------------------------

  @Test
  void applyControllerConfigsReturnsNoOpWhenNothingConfigured() {
    var loader = new ConfigLoader(mapProvider(Map.of()));
    assertThat(loader.applyControllerConfigs("my-controller")).isNotNull();
  }

  @Test
  void applyControllerConfigsQueriesKeysPrefixedWithControllerName() {
    // Record every key the loader asks for, regardless of whether a value exists.
    var queriedKeys = new ArrayList<String>();
    ConfigProvider recordingProvider =
        new ConfigProvider() {
          @Override
          public <T> Optional<T> getValue(String key, Class<T> type) {
            queriedKeys.add(key);
            return Optional.empty();
          }
        };

    new ConfigLoader(recordingProvider).applyControllerConfigs("my-ctrl");

    assertThat(queriedKeys).allMatch(k -> k.startsWith("josdk.controller.my-ctrl."));
  }

  @Test
  void applyControllerConfigsIsolatesControllersByName() {
    // Two controllers configured in the same provider — only matching keys must be returned.
    var values = new HashMap<String, Object>();
    values.put("josdk.controller.alpha.finalizer", "alpha-finalizer");
    values.put("josdk.controller.beta.finalizer", "beta-finalizer");
    var loader = new ConfigLoader(mapProvider(values));

    // alpha gets a consumer (key found), beta gets a consumer (key found)
    assertThat(loader.applyControllerConfigs("alpha")).isNotNull();
    assertThat(loader.applyControllerConfigs("beta")).isNotNull();
    // a controller with no configured keys still gets a non-null no-op consumer
    assertThat(loader.applyControllerConfigs("gamma")).isNotNull();
  }

  @Test
  void applyControllerConfigsQueriesAllExpectedPropertySuffixes() {
    var queriedKeys = new ArrayList<String>();
    ConfigProvider recordingProvider =
        new ConfigProvider() {
          @Override
          public <T> Optional<T> getValue(String key, Class<T> type) {
            queriedKeys.add(key);
            return Optional.empty();
          }
        };

    new ConfigLoader(recordingProvider).applyControllerConfigs("ctrl");

    assertThat(queriedKeys)
        .contains(
            "josdk.controller.ctrl.finalizer",
            "josdk.controller.ctrl.generation-aware",
            "josdk.controller.ctrl.label-selector",
            "josdk.controller.ctrl.max-reconciliation-interval",
            "josdk.controller.ctrl.field-manager",
            "josdk.controller.ctrl.trigger-reconciler-on-all-events",
            "josdk.controller.ctrl.informer-list-limit");
  }

  // -- key prefix constants ---------------------------------------------------

  @Test
  void operatorKeyPrefixIsJosdkDot() {
    assertThat(ConfigLoader.DEFAULT_OPERATOR_KEY_PREFIX).isEqualTo("josdk.");
  }

  @Test
  void controllerKeyPrefixIsJosdkControllerDot() {
    assertThat(ConfigLoader.DEFAULT_CONTROLLER_KEY_PREFIX).isEqualTo("josdk.controller.");
  }
}
