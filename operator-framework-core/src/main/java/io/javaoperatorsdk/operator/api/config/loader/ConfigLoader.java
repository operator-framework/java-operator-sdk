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
import java.util.List;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;

public class ConfigLoader {

  public static final ConfigLoader DEFAULT = new ConfigLoader();

  /**
   * Key prefix for operator-level (ConfigurationService) properties, e.g. {@code
   * josdk.concurrent.reconciliation.threads}.
   */
  public static final String OPERATOR_KEY_PREFIX = "josdk.";

  /**
   * Key prefix for controller-level properties. The controller name is inserted between this prefix
   * and the property name, e.g. {@code josdk.controller.my-controller.finalizer}.
   */
  public static final String CONTROLLER_KEY_PREFIX = "josdk.controller.";

  // ---------------------------------------------------------------------------
  // Operator-level (ConfigurationServiceOverrider) bindings
  // Only scalar / value types that a key-value ConfigProvider can supply are
  // included. Complex objects (KubernetesClient, ExecutorService, …) must be
  // configured programmatically and are intentionally omitted.
  // ---------------------------------------------------------------------------
  private static final List<ConfigBinding<ConfigurationServiceOverrider, ?>> OPERATOR_BINDINGS =
      List.of(
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "check.crd.and.validate.local.model",
              Boolean.class,
              ConfigurationServiceOverrider::checkingCRDAndValidateLocalModel),
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "concurrent.reconciliation.threads",
              Integer.class,
              ConfigurationServiceOverrider::withConcurrentReconciliationThreads),
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "concurrent.workflow.executor.threads",
              Integer.class,
              ConfigurationServiceOverrider::withConcurrentWorkflowExecutorThreads),
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "close.client.on.stop",
              Boolean.class,
              ConfigurationServiceOverrider::withCloseClientOnStop),
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "stop.on.informer.error.during.startup",
              Boolean.class,
              ConfigurationServiceOverrider::withStopOnInformerErrorDuringStartup),
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "cache.sync.timeout",
              Duration.class,
              ConfigurationServiceOverrider::withCacheSyncTimeout),
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "reconciliation.termination.timeout",
              Duration.class,
              ConfigurationServiceOverrider::withReconciliationTerminationTimeout),
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "ssa.based.create.update.match.for.dependent.resources",
              Boolean.class,
              ConfigurationServiceOverrider::withSSABasedCreateUpdateMatchForDependentResources),
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "use.ssa.to.patch.primary.resource",
              Boolean.class,
              ConfigurationServiceOverrider::withUseSSAToPatchPrimaryResource),
          new ConfigBinding<>(
              OPERATOR_KEY_PREFIX + "clone.secondary.resources.when.getting.from.cache",
              Boolean.class,
              ConfigurationServiceOverrider::withCloneSecondaryResourcesWhenGettingFromCache));

  // ---------------------------------------------------------------------------
  // Controller-level (ControllerConfigurationOverrider) bindings
  // The key used at runtime is built as:
  //   CONTROLLER_KEY_PREFIX + controllerName + "." + <suffix>
  // ---------------------------------------------------------------------------
  private static final List<ConfigBinding<ControllerConfigurationOverrider<?>, ?>>
      CONTROLLER_BINDINGS =
          List.of(
              new ConfigBinding<>(
                  "finalizer", String.class, ControllerConfigurationOverrider::withFinalizer),
              new ConfigBinding<>(
                  "generation.aware",
                  Boolean.class,
                  ControllerConfigurationOverrider::withGenerationAware),
              new ConfigBinding<>(
                  "label.selector",
                  String.class,
                  ControllerConfigurationOverrider::withLabelSelector),
              new ConfigBinding<>(
                  "reconciliation.max.interval",
                  Duration.class,
                  ControllerConfigurationOverrider::withReconciliationMaxInterval),
              new ConfigBinding<>(
                  "field.manager",
                  String.class,
                  ControllerConfigurationOverrider::withFieldManager),
              new ConfigBinding<>(
                  "trigger.reconciler.on.all.events",
                  Boolean.class,
                  ControllerConfigurationOverrider::withTriggerReconcilerOnAllEvents),
              new ConfigBinding<>(
                  "informer.list.limit",
                  Long.class,
                  ControllerConfigurationOverrider::withInformerListLimit));

  private final ConfigProvider configProvider;

  public ConfigLoader() {
    this(new DefaultConfigProvider());
  }

  public ConfigLoader(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  /**
   * Returns a {@link Consumer} that applies every operator-level property found in the {@link
   * ConfigProvider} to the given {@link ConfigurationServiceOverrider}. Returns {@code null} when
   * no binding has a matching value, preserving the previous behaviour.
   */
  public Consumer<ConfigurationServiceOverrider> applyConfigs() {
    return buildConsumer(OPERATOR_BINDINGS, null);
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
    String prefix = CONTROLLER_KEY_PREFIX + controllerName + ".";
    // Cast is safe: the setter BiConsumer<ControllerConfigurationOverrider<?>, T> is covariant in
    // its first parameter for our usage – we only ever call it with
    // ControllerConfigurationOverrider<R>.
    List<ConfigBinding<ControllerConfigurationOverrider<R>, ?>> bindings =
        (List<ConfigBinding<ControllerConfigurationOverrider<R>, ?>>) (List<?>) CONTROLLER_BINDINGS;
    return buildConsumer(bindings, prefix);
  }

  /**
   * Iterates {@code bindings} and, for each one whose key (optionally prefixed by {@code
   * keyPrefix}) is present in the {@link ConfigProvider}, accumulates a call to the binding's
   * setter.
   *
   * @param bindings the predefined bindings to check
   * @param keyPrefix when non-null the key stored in the binding is treated as a suffix and this
   *     prefix is prepended before the lookup
   * @return a consumer that applies all found values, or {@code null} if none were found
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
    return consumer;
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
