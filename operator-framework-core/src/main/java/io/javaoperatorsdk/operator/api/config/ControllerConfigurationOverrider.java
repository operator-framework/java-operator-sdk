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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.Retry;

@SuppressWarnings({"rawtypes", "unused", "UnusedReturnValue"})
public class ControllerConfigurationOverrider<R extends HasMetadata> {

  private final ControllerConfiguration<R> original;
  private String name;
  private String finalizer;
  private boolean generationAware;
  private Retry retry;
  private RateLimiter rateLimiter;
  private String fieldManager;
  private Duration reconciliationMaxInterval;
  private Map<DependentResourceSpec, Object> configurations;
  private final InformerConfiguration<R>.Builder config;
  private boolean triggerReconcilerOnAllEvents;

  private ControllerConfigurationOverrider(ControllerConfiguration<R> original) {
    this.finalizer = original.getFinalizerName();
    this.generationAware = original.isGenerationAware();
    final var informerConfig = original.getInformerConfig();
    this.config = InformerConfiguration.builder(informerConfig);
    this.retry = original.getRetry();
    this.reconciliationMaxInterval = original.maxReconciliationInterval().orElse(null);
    this.original = original;
    this.rateLimiter = original.getRateLimiter();
    this.name = original.getName();
    this.fieldManager = original.fieldManager();
    this.triggerReconcilerOnAllEvents = original.triggerReconcilerOnAllEvents();
  }

  public ControllerConfigurationOverrider<R> withFinalizer(String finalizer) {
    this.finalizer = finalizer;
    return this;
  }

  public ControllerConfigurationOverrider<R> withGenerationAware(boolean generationAware) {
    this.generationAware = generationAware;
    return this;
  }

  public ControllerConfigurationOverrider<R> watchingOnlyCurrentNamespace() {
    config.withWatchCurrentNamespace();
    return this;
  }

  public ControllerConfigurationOverrider<R> addingNamespaces(String... namespaces) {
    if (namespaces != null && namespaces.length > 0) {
      final var current = config.namespaces();
      final var aggregated = new HashSet<String>(current.size() + namespaces.length);
      aggregated.addAll(current);
      aggregated.addAll(Set.of(namespaces));
      config.withNamespaces(aggregated);
    }
    return this;
  }

  public ControllerConfigurationOverrider<R> removingNamespaces(String... namespaces) {
    if (namespaces != null && namespaces.length > 0) {
      final var current = new HashSet<>(config.namespaces());
      List.of(namespaces).forEach(current::remove);
      if (current.isEmpty()) {
        return watchingAllNamespaces();
      } else {
        config.withNamespaces(current);
      }
    }
    return this;
  }

  public ControllerConfigurationOverrider<R> settingNamespaces(Set<String> newNamespaces) {
    config.withNamespaces(newNamespaces);
    return this;
  }

  public ControllerConfigurationOverrider<R> settingNamespaces(String... newNamespaces) {
    return settingNamespaces(Set.of(newNamespaces));
  }

  public ControllerConfigurationOverrider<R> settingNamespace(String namespace) {
    config.withNamespaces(Set.of(namespace));
    return this;
  }

  public ControllerConfigurationOverrider<R> watchingAllNamespaces() {
    config.withWatchAllNamespaces();
    return this;
  }

  public ControllerConfigurationOverrider<R> withRetry(Retry retry) {
    this.retry = retry;
    return this;
  }

  public ControllerConfigurationOverrider<R> withRateLimiter(RateLimiter rateLimiter) {
    this.rateLimiter = rateLimiter;
    return this;
  }

  public ControllerConfigurationOverrider<R> withLabelSelector(String labelSelector) {
    config.withLabelSelector(labelSelector);
    return this;
  }

  public ControllerConfigurationOverrider<R> withReconciliationMaxInterval(
      Duration reconciliationMaxInterval) {
    this.reconciliationMaxInterval = reconciliationMaxInterval;
    return this;
  }

  public ControllerConfigurationOverrider<R> withOnAddFilter(OnAddFilter<R> onAddFilter) {
    config.withOnAddFilter(onAddFilter);
    return this;
  }

  public ControllerConfigurationOverrider<R> withOnUpdateFilter(OnUpdateFilter<R> onUpdateFilter) {
    config.withOnUpdateFilter(onUpdateFilter);
    return this;
  }

  public ControllerConfigurationOverrider<R> withGenericFilter(GenericFilter<R> genericFilter) {
    config.withGenericFilter(genericFilter);
    return this;
  }

  public ControllerConfigurationOverrider<R> withItemStore(ItemStore<R> itemStore) {
    config.withItemStore(itemStore);
    return this;
  }

  public ControllerConfigurationOverrider<R> withName(String name) {
    this.name = name;
    config.withName(name);
    return this;
  }

  public ControllerConfigurationOverrider<R> withFieldManager(String dependentFieldManager) {
    this.fieldManager = dependentFieldManager;
    return this;
  }

  /**
   * @deprecated use {@link #withTriggerReconcilerOnAllEvents(boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public ControllerConfigurationOverrider<R> withTriggerReconcilerOnAllEvent(
      boolean triggerReconcilerOnAllEvents) {
    return withTriggerReconcilerOnAllEvents(triggerReconcilerOnAllEvents);
  }

  public ControllerConfigurationOverrider<R> withTriggerReconcilerOnAllEvents(
      boolean triggerReconcilerOnAllEvents) {
    this.triggerReconcilerOnAllEvents = triggerReconcilerOnAllEvents;
    return this;
  }

  /**
   * Sets a max page size limit when starting the informer. This will result in pagination while
   * populating the cache. This means that longer lists will take multiple requests to fetch. See
   * {@link io.fabric8.kubernetes.client.dsl.Informable#withLimit(Long)} for more details.
   *
   * @param informerListLimit null (the default) results in no pagination
   */
  public ControllerConfigurationOverrider<R> withInformerListLimit(Long informerListLimit) {
    config.withInformerListLimit(informerListLimit);
    return this;
  }

  public ControllerConfigurationOverrider<R> replacingNamedDependentResourceConfig(
      String name, Object dependentResourceConfig) {

    final var specs = original.getWorkflowSpec().orElseThrow().getDependentResourceSpecs();
    final var spec =
        specs.stream()
            .filter(drs -> drs.getName().equals(name))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException("Cannot find a DependentResource named: " + name));

    if (configurations == null) {
      configurations = new HashMap<>(specs.size());
    }
    configurations.put(spec, dependentResourceConfig);
    return this;
  }

  public ControllerConfiguration<R> build() {
    return new ResolvedControllerConfiguration<>(
        name,
        generationAware,
        original.getAssociatedReconcilerClassName(),
        retry,
        rateLimiter,
        reconciliationMaxInterval,
        finalizer,
        configurations,
        fieldManager,
        original.getConfigurationService(),
        config.buildForController(),
        triggerReconcilerOnAllEvents,
        original.getWorkflowSpec().orElse(null));
  }

  public static <R extends HasMetadata> ControllerConfigurationOverrider<R> override(
      ControllerConfiguration<R> original) {
    return new ControllerConfigurationOverrider<>(original);
  }
}
