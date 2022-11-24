package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.DEFAULT_NAMESPACES_SET;
import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE_SET;

@SuppressWarnings({"rawtypes", "unused"})
public class ControllerConfigurationOverrider<R extends HasMetadata> {

  private String finalizer;
  private boolean generationAware;
  private Set<String> namespaces;
  private Retry retry;
  private String labelSelector;
  private ResourceEventFilter<R> customResourcePredicate;
  private final ControllerConfiguration<R> original;
  private Duration reconciliationMaxInterval;
  private final LinkedHashMap<String, DependentResourceSpec> namedDependentResourceSpecs;
  private OnAddFilter<R> onAddFilter;
  private OnUpdateFilter<R> onUpdateFilter;
  private GenericFilter<R> genericFilter;
  private RateLimiter rateLimiter;
  private ItemStore<R> itemStore;

  private ControllerConfigurationOverrider(ControllerConfiguration<R> original) {
    finalizer = original.getFinalizerName();
    generationAware = original.isGenerationAware();
    namespaces = new HashSet<>(original.getNamespaces());
    retry = original.getRetry();
    labelSelector = original.getLabelSelector();
    customResourcePredicate = original.getEventFilter();
    reconciliationMaxInterval = original.maxReconciliationInterval().orElse(null);
    // make the original specs modifiable
    final var dependentResources = original.getDependentResources();
    namedDependentResourceSpecs = new LinkedHashMap<>(dependentResources.size());
    this.onAddFilter = original.onAddFilter().orElse(null);
    this.onUpdateFilter = original.onUpdateFilter().orElse(null);
    this.genericFilter = original.genericFilter().orElse(null);
    dependentResources.forEach(drs -> namedDependentResourceSpecs.put(drs.getName(), drs));
    this.original = original;
    this.rateLimiter = original.getRateLimiter();
    this.itemStore = original.itemStore().orElse(null);
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
    this.namespaces = WATCH_CURRENT_NAMESPACE_SET;
    return this;
  }

  public ControllerConfigurationOverrider<R> addingNamespaces(String... namespaces) {
    this.namespaces.addAll(List.of(namespaces));
    return this;
  }

  public ControllerConfigurationOverrider<R> removingNamespaces(String... namespaces) {
    List.of(namespaces).forEach(this.namespaces::remove);
    if (this.namespaces.isEmpty()) {
      this.namespaces = DEFAULT_NAMESPACES_SET;
    }
    return this;
  }

  public ControllerConfigurationOverrider<R> settingNamespaces(Set<String> newNamespaces) {
    this.namespaces.clear();
    this.namespaces.addAll(newNamespaces);
    return this;
  }

  public ControllerConfigurationOverrider<R> settingNamespaces(String... newNamespaces) {
    return settingNamespaces(Set.of(newNamespaces));
  }

  public ControllerConfigurationOverrider<R> settingNamespace(String namespace) {
    this.namespaces.clear();
    this.namespaces.add(namespace);
    return this;
  }

  public ControllerConfigurationOverrider<R> watchingAllNamespaces() {
    this.namespaces = DEFAULT_NAMESPACES_SET;
    return this;
  }

  /**
   * @deprecated Use {@link #withRetry(Retry)} instead
   */
  @Deprecated(forRemoval = true)
  public ControllerConfigurationOverrider<R> withRetry(RetryConfiguration retry) {
    this.retry = GenericRetry.fromConfiguration(retry);
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
    this.labelSelector = labelSelector;
    return this;
  }

  public ControllerConfigurationOverrider<R> withCustomResourcePredicate(
      ResourceEventFilter<R> customResourcePredicate) {
    this.customResourcePredicate = customResourcePredicate;
    return this;
  }

  public ControllerConfigurationOverrider<R> withReconciliationMaxInterval(
      Duration reconciliationMaxInterval) {
    this.reconciliationMaxInterval = reconciliationMaxInterval;
    return this;
  }

  public ControllerConfigurationOverrider<R> withOnAddFilter(OnAddFilter<R> onAddFilter) {
    this.onAddFilter = onAddFilter;
    return this;
  }

  public ControllerConfigurationOverrider<R> withOnUpdateFilter(OnUpdateFilter<R> onUpdateFilter) {
    this.onUpdateFilter = onUpdateFilter;
    return this;
  }

  public ControllerConfigurationOverrider<R> withGenericFilter(GenericFilter<R> genericFilter) {
    this.genericFilter = genericFilter;
    return this;
  }

  public ControllerConfigurationOverrider<R> withItemStore(ItemStore<R> itemStore) {
    this.itemStore = itemStore;
    return this;
  }

  @SuppressWarnings("unchecked")
  public ControllerConfigurationOverrider<R> replacingNamedDependentResourceConfig(String name,
      Object dependentResourceConfig) {

    var current = namedDependentResourceSpecs.get(name);
    if (current == null) {
      throw new IllegalArgumentException("Cannot find a DependentResource named: " + name);
    }

    var dependentResource = current.getDependentResource();
    if (dependentResource instanceof DependentResourceConfigurator) {
      var configurator = (DependentResourceConfigurator) dependentResource;
      configurator.configureWith(dependentResourceConfig);
    }

    return this;
  }

  public ControllerConfiguration<R> build() {
    final var hasModifiedNamespaces = !original.getNamespaces().equals(namespaces);
    final var newDependentSpecs = namedDependentResourceSpecs.values().stream()
        .peek(spec -> {
          // if the dependent resource has a NamespaceChangeable config
          // update the namespaces if needed, otherwise, do nothing
          if (hasModifiedNamespaces) {
            final Optional<?> maybeConfig = spec.getDependentResourceConfiguration();
            maybeConfig
                .filter(NamespaceChangeable.class::isInstance)
                .map(NamespaceChangeable.class::cast)
                .filter(NamespaceChangeable::allowsNamespaceChanges)
                .ifPresent(nc -> nc.changeNamespaces(namespaces));
          }
        }).collect(Collectors.toList());

    return new DefaultControllerConfiguration<>(
        original.getAssociatedReconcilerClassName(),
        original.getName(),
        original.getResourceTypeName(),
        finalizer,
        generationAware,
        namespaces,
        retry,
        labelSelector,
        customResourcePredicate,
        original.getResourceClass(),
        reconciliationMaxInterval,
        onAddFilter,
        onUpdateFilter,
        genericFilter,
        rateLimiter,
        newDependentSpecs, itemStore);
  }

  public static <R extends HasMetadata> ControllerConfigurationOverrider<R> override(
      ControllerConfiguration<R> original) {
    return new ControllerConfigurationOverrider<>(original);
  }
}
