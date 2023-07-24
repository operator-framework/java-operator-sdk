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
  private OnAddFilter<? super R> onAddFilter;
  private OnUpdateFilter<? super R> onUpdateFilter;
  private GenericFilter<? super R> genericFilter;
  private RateLimiter rateLimiter;
  private Map<DependentResourceSpec, Object> configurations;
  private ItemStore<R> itemStore;
  private String name;
  private String fieldManager;
  private Long informerListLimit;

  private ControllerConfigurationOverrider(ControllerConfiguration<R> original) {
    this.finalizer = original.getFinalizerName();
    this.generationAware = original.isGenerationAware();
    this.namespaces = new HashSet<>(original.getNamespaces());
    this.retry = original.getRetry();
    this.labelSelector = original.getLabelSelector();
    this.customResourcePredicate = original.getEventFilter();
    this.reconciliationMaxInterval = original.maxReconciliationInterval().orElse(null);
    this.onAddFilter = original.onAddFilter().orElse(null);
    this.onUpdateFilter = original.onUpdateFilter().orElse(null);
    this.genericFilter = original.genericFilter().orElse(null);
    this.original = original;
    this.rateLimiter = original.getRateLimiter();
    this.name = original.getName();
    this.fieldManager = original.fieldManager();
    this.informerListLimit = original.getInformerListLimit().orElse(null);
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
   * @param retry configuration
   * @return current instance of overrider
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

  public ControllerConfigurationOverrider<R> withName(String name) {
    this.name = name;
    return this;
  }

  public ControllerConfigurationOverrider<R> withFieldManager(
      String dependentFieldManager) {
    this.fieldManager = dependentFieldManager;
    return this;
  }


  /**
   * Sets a max page size limit when starting the informer. This will result in pagination while
   * populating the cache. This means that longer lists will take multiple requests to fetch. See
   * {@link io.fabric8.kubernetes.client.dsl.Informable#withLimit(Long)} for more details.
   *
   * @param informerListLimit null (the default) results in no pagination
   */
  public ControllerConfigurationOverrider<R> withInformerListLimit(
      Long informerListLimit) {
    this.informerListLimit = informerListLimit;
    return this;
  }

  public ControllerConfigurationOverrider<R> replacingNamedDependentResourceConfig(String name,
      Object dependentResourceConfig) {

    final var specs = original.getDependentResources();
    final var spec = specs.stream()
        .filter(drs -> drs.getName().equals(name)).findFirst()
        .orElseThrow(
            () -> new IllegalArgumentException("Cannot find a DependentResource named: " + name));

    if (configurations == null) {
      configurations = new HashMap<>(specs.size());
    }
    configurations.put(spec, dependentResourceConfig);
    return this;
  }

  public ControllerConfiguration<R> build() {
    final var overridden = new ResolvedControllerConfiguration<>(original.getResourceClass(),
        name,
        generationAware, original.getAssociatedReconcilerClassName(), retry, rateLimiter,
        reconciliationMaxInterval, onAddFilter, onUpdateFilter, genericFilter,
        original.getDependentResources(),
        namespaces, finalizer, labelSelector, configurations, itemStore, fieldManager,
        original.getConfigurationService(), informerListLimit);
    overridden.setEventFilter(customResourcePredicate);
    return overridden;
  }

  public static <R extends HasMetadata> ControllerConfigurationOverrider<R> override(
      ControllerConfiguration<R> original) {
    return new ControllerConfigurationOverrider<>(original);
  }
}
