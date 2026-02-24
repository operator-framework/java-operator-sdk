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
package io.javaoperatorsdk.operator.config.loader;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;

public class ConfigLoader {

  private static final ConfigLoader DEFAULT = new ConfigLoader();

  public static ConfigLoader getDefault() {
    return DEFAULT;
  }

  public static final String DEFAULT_OPERATOR_KEY_PREFIX = "josdk.";
  public static final String DEFAULT_CONTROLLER_KEY_PREFIX = "josdk.controller.";

  /**
   * Key prefix for controller-level properties. The controller name is inserted between this prefix
   * and the property name, e.g. {@code josdk.controller.my-controller.finalizer}.
   */
  private final String controllerKeyPrefix;

  private final String operatorKeyPrefix;

  // ---------------------------------------------------------------------------
  // Operator-level (ConfigurationServiceOverrider) bindings
  // Only scalar / value types that a key-value ConfigProvider can supply are
  // included. Complex objects (KubernetesClient, ExecutorService, …) must be
  // configured programmatically and are intentionally omitted.
  // ---------------------------------------------------------------------------
  static final List<ConfigBinding<ConfigurationServiceOverrider, ?>> OPERATOR_BINDINGS =
      List.of(
          new ConfigBinding<>(
              "check-crd",
              Boolean.class,
              ConfigurationServiceOverrider::checkingCRDAndValidateLocalModel),
          new ConfigBinding<>(
              "reconciliation.termination-timeout",
              Duration.class,
              ConfigurationServiceOverrider::withReconciliationTerminationTimeout),
          new ConfigBinding<>(
              "reconciliation.concurrent-threads",
              Integer.class,
              ConfigurationServiceOverrider::withConcurrentReconciliationThreads),
          new ConfigBinding<>(
              "workflow.executor-threads",
              Integer.class,
              ConfigurationServiceOverrider::withConcurrentWorkflowExecutorThreads),
          new ConfigBinding<>(
              "close-client-on-stop",
              Boolean.class,
              ConfigurationServiceOverrider::withCloseClientOnStop),
          new ConfigBinding<>(
              "informer.stop-on-error-during-startup",
              Boolean.class,
              ConfigurationServiceOverrider::withStopOnInformerErrorDuringStartup),
          new ConfigBinding<>(
              "informer.cache-sync-timeout",
              Duration.class,
              ConfigurationServiceOverrider::withCacheSyncTimeout),
          new ConfigBinding<>(
              "dependent-resources.ssa-based-create-update-match",
              Boolean.class,
              ConfigurationServiceOverrider::withSSABasedCreateUpdateMatchForDependentResources),
          new ConfigBinding<>(
              "use-ssa-to-patch-primary-resource",
              Boolean.class,
              ConfigurationServiceOverrider::withUseSSAToPatchPrimaryResource),
          new ConfigBinding<>(
              "clone-secondary-resources-when-getting-from-cache",
              Boolean.class,
              ConfigurationServiceOverrider::withCloneSecondaryResourcesWhenGettingFromCache));

  // ---------------------------------------------------------------------------
  // Controller-level retry property suffixes
  // ---------------------------------------------------------------------------
  static final String RETRY_MAX_ATTEMPTS_SUFFIX = "retry.max-attempts";
  static final String RETRY_INITIAL_INTERVAL_SUFFIX = "retry.initial-interval";
  static final String RETRY_INTERVAL_MULTIPLIER_SUFFIX = "retry.interval-multiplier";
  static final String RETRY_MAX_INTERVAL_SUFFIX = "retry.max-interval";

  // ---------------------------------------------------------------------------
  // Controller-level (ControllerConfigurationOverrider) bindings
  // The key used at runtime is built as:
  //   CONTROLLER_KEY_PREFIX + controllerName + "." + <suffix>
  // ---------------------------------------------------------------------------
  static final List<ConfigBinding<ControllerConfigurationOverrider<?>, ?>> CONTROLLER_BINDINGS =
      List.of(
          new ConfigBinding<>(
              "finalizer", String.class, ControllerConfigurationOverrider::withFinalizer),
          new ConfigBinding<>(
              "generation-aware",
              Boolean.class,
              ControllerConfigurationOverrider::withGenerationAware),
          new ConfigBinding<>(
              "label-selector", String.class, ControllerConfigurationOverrider::withLabelSelector),
          new ConfigBinding<>(
              "max-reconciliation-interval",
              Duration.class,
              ControllerConfigurationOverrider::withReconciliationMaxInterval),
          new ConfigBinding<>(
              "field-manager", String.class, ControllerConfigurationOverrider::withFieldManager),
          new ConfigBinding<>(
              "trigger-reconciler-on-all-events",
              Boolean.class,
              ControllerConfigurationOverrider::withTriggerReconcilerOnAllEvents),
          new ConfigBinding<>(
              "informer-list-limit",
              Long.class,
              ControllerConfigurationOverrider::withInformerListLimit));

  private final ConfigProvider configProvider;

  public ConfigLoader() {
    this(new DefaultConfigProvider(), DEFAULT_CONTROLLER_KEY_PREFIX, DEFAULT_OPERATOR_KEY_PREFIX);
  }

  public ConfigLoader(ConfigProvider configProvider) {
    this(configProvider, DEFAULT_CONTROLLER_KEY_PREFIX, DEFAULT_OPERATOR_KEY_PREFIX);
  }

  public ConfigLoader(
      ConfigProvider configProvider, String controllerKeyPrefix, String operatorKeyPrefix) {
    this.configProvider = configProvider;
    this.controllerKeyPrefix = controllerKeyPrefix;
    this.operatorKeyPrefix = operatorKeyPrefix;
  }

  /**
   * Returns a {@link Consumer} that applies every operator-level property found in the {@link
   * ConfigProvider} to the given {@link ConfigurationServiceOverrider}. Returns no-op consumer when
   * no binding has a matching value, preserving the previous behavior.
   */
  public Consumer<ConfigurationServiceOverrider> applyConfigs() {
    return buildConsumer(OPERATOR_BINDINGS, operatorKeyPrefix);
  }

  /**
   * Returns a {@link Consumer} that applies every controller-level property found in the {@link
   * ConfigProvider} to the given {@link ControllerConfigurationOverrider}. The keys are looked up
   * as {@code josdk.controller.<controllerName>.<property>}. Returns {@code null} when no binding
   * has a matching value.
   */
  @SuppressWarnings("unchecked")
  public <R extends HasMetadata>
      Consumer<ControllerConfigurationOverrider<R>> applyControllerConfigs(String controllerName) {
    String prefix = controllerKeyPrefix + controllerName + ".";
    // Cast is safe: the setter BiConsumer<ControllerConfigurationOverrider<?>, T> is covariant in
    // its first parameter for our usage – we only ever call it with
    // ControllerConfigurationOverrider<R>.
    List<ConfigBinding<ControllerConfigurationOverrider<R>, ?>> bindings =
        (List<ConfigBinding<ControllerConfigurationOverrider<R>, ?>>) (List<?>) CONTROLLER_BINDINGS;
    Consumer<ControllerConfigurationOverrider<R>> consumer = buildConsumer(bindings, prefix);

    Consumer<ControllerConfigurationOverrider<R>> retryStep = buildRetryConsumer(prefix);
    if (retryStep != null) {
      consumer = consumer == null ? retryStep : consumer.andThen(retryStep);
    }
    return consumer;
  }

  /**
   * If at least one retry property is present for the given prefix, returns a {@link Consumer} that
   * builds a {@link GenericRetry} starting from {@link GenericRetry#defaultLimitedExponentialRetry}
   * and overrides only the properties that are explicitly set.
   */
  private <R extends HasMetadata> Consumer<ControllerConfigurationOverrider<R>> buildRetryConsumer(
      String prefix) {
    Optional<Integer> maxAttempts =
        configProvider.getValue(prefix + RETRY_MAX_ATTEMPTS_SUFFIX, Integer.class);
    Optional<Long> initialInterval =
        configProvider.getValue(prefix + RETRY_INITIAL_INTERVAL_SUFFIX, Long.class);
    Optional<Double> intervalMultiplier =
        configProvider.getValue(prefix + RETRY_INTERVAL_MULTIPLIER_SUFFIX, Double.class);
    Optional<Long> maxInterval =
        configProvider.getValue(prefix + RETRY_MAX_INTERVAL_SUFFIX, Long.class);

    if (maxAttempts.isEmpty()
        && initialInterval.isEmpty()
        && intervalMultiplier.isEmpty()
        && maxInterval.isEmpty()) {
      return null;
    }

    return overrider -> {
      GenericRetry retry = GenericRetry.defaultLimitedExponentialRetry();
      maxAttempts.ifPresent(retry::setMaxAttempts);
      initialInterval.ifPresent(retry::setInitialInterval);
      intervalMultiplier.ifPresent(retry::setIntervalMultiplier);
      maxInterval.ifPresent(retry::setMaxInterval);
      overrider.withRetry(retry);
    };
  }

  /**
   * Iterates {@code bindings} and, for each one whose key (optionally prefixed by {@code
   * keyPrefix}) is present in the {@link ConfigProvider}, accumulates a call to the binding's
   * setter.
   *
   * @param bindings the predefined bindings to check
   * @param keyPrefix when non-null the key stored in the binding is treated as a suffix and this
   *     prefix is prepended before the lookup
   * @return a consumer that applies all found values, or a no-op consumer if none were found
   */
  private <O> Consumer<O> buildConsumer(List<ConfigBinding<O, ?>> bindings, String keyPrefix) {
    Consumer<O> consumer = null;
    for (var binding : bindings) {
      String lookupKey = keyPrefix == null ? binding.key() : keyPrefix + binding.key();
      Consumer<O> step = resolveStep(binding, lookupKey);
      if (step != null) {
        consumer = consumer == null ? step : consumer.andThen(step);
      }
    }
    return consumer == null ? o -> {} : consumer;
  }

  /**
   * Queries the {@link ConfigProvider} for {@code key} with the binding's type. If a value is
   * present, returns a {@link Consumer} that calls the binding's setter; otherwise returns {@code
   * null}.
   */
  private <O, T> Consumer<O> resolveStep(ConfigBinding<O, T> binding, String key) {
    return configProvider
        .getValue(key, binding.type())
        .map(value -> (Consumer<O>) overrider -> binding.setter().accept(overrider, value))
        .orElse(null);
  }
}
