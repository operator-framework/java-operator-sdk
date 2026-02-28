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
package io.javaoperatorsdk.operator.baseapi.configloader;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;
import io.javaoperatorsdk.operator.config.loader.ConfigLoader;
import io.javaoperatorsdk.operator.config.loader.ConfigProvider;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.support.TestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests that verify {@link ConfigLoader} property overrides take effect when wiring up
 * a real operator instance via {@link LocallyRunOperatorExtension}.
 *
 * <p>Each nested class exercises a distinct group of properties so that failures are easy to
 * pinpoint.
 */
class ConfigLoaderIT {

  /** Builds a {@link ConfigProvider} backed by a plain map. */
  private static ConfigProvider mapProvider(Map<String, Object> values) {
    return new ConfigProvider() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> Optional<T> getValue(String key, Class<T> type) {
        return Optional.ofNullable((T) values.get(key));
      }
    };
  }

  // ---------------------------------------------------------------------------
  // Operator-level properties
  // ---------------------------------------------------------------------------

  @Nested
  class OperatorLevelProperties {

    /**
     * Verifies that {@code josdk.reconciliation.concurrent-threads} loaded via {@link ConfigLoader}
     * and applied through {@code withConfigurationService} actually changes the operator's thread
     * pool size.
     */
    @RegisterExtension
    LocallyRunOperatorExtension operator =
        LocallyRunOperatorExtension.builder()
            .withReconciler(new ConfigLoaderTestReconciler(0))
            .withConfigurationService(
                new ConfigLoader(mapProvider(Map.of("josdk.reconciliation.concurrent-threads", 2)))
                    .applyConfigs())
            .build();

    @Test
    void concurrentReconciliationThreadsIsAppliedFromConfigLoader() {
      assertThat(operator.getOperator().getConfigurationService().concurrentReconciliationThreads())
          .isEqualTo(2);
    }
  }

  // ---------------------------------------------------------------------------
  // Controller-level retry
  // ---------------------------------------------------------------------------

  @Nested
  class ControllerRetryProperties {

    static final int FAILS = 2;
    // controller name is the lower-cased simple class name by default
    static final String CTRL_NAME = ConfigLoaderTestReconciler.class.getSimpleName().toLowerCase();

    /**
     * Verifies that retry properties read by {@link ConfigLoader} for a specific controller name
     * are applied when registering the reconciler via a {@code configurationOverrider} consumer,
     * and that the resulting operator actually retries and eventually succeeds.
     */
    @RegisterExtension
    LocallyRunOperatorExtension operator =
        LocallyRunOperatorExtension.builder()
            .withReconciler(
                new ConfigLoaderTestReconciler(FAILS),
                // applyControllerConfigs returns Consumer<ControllerConfigurationOverrider<R>>;
                // withReconciler takes the raw Consumer<ControllerConfigurationOverrider>
                (Consumer<ControllerConfigurationOverrider>)
                    (Consumer<?>)
                        new ConfigLoader(
                                mapProvider(
                                    Map.of(
                                        "josdk.controller." + CTRL_NAME + ".retry.max-attempts",
                                        5,
                                        "josdk.controller." + CTRL_NAME + ".retry.initial-interval",
                                        100L)))
                            .applyControllerConfigs(CTRL_NAME))
            .build();

    @Test
    void retryConfigFromConfigLoaderIsAppliedAndReconcilerEventuallySucceeds() {
      var resource = createResource("1");
      operator.create(resource);

      await("reconciler succeeds after retries")
          .atMost(10, TimeUnit.SECONDS)
          .pollInterval(100, TimeUnit.MILLISECONDS)
          .untilAsserted(
              () -> {
                assertThat(TestUtils.getNumberOfExecutions(operator)).isEqualTo(FAILS + 1);
                var updated =
                    operator.get(
                        ConfigLoaderTestCustomResource.class, resource.getMetadata().getName());
                assertThat(updated.getStatus()).isNotNull();
                assertThat(updated.getStatus().getState())
                    .isEqualTo(ConfigLoaderTestCustomResourceStatus.State.SUCCESS);
              });
    }

    private ConfigLoaderTestCustomResource createResource(String id) {
      var resource = new ConfigLoaderTestCustomResource();
      resource.setMetadata(new ObjectMetaBuilder().withName("cfgloader-retry-" + id).build());
      return resource;
    }
  }
}
