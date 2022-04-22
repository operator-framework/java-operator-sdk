package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

@SuppressWarnings({"rawtypes", "unused"})
public class ControllerConfigurationOverrider<R extends HasMetadata> {

  private String finalizer;
  private boolean generationAware;
  private Set<String> namespaces;
  private RetryConfiguration retry;
  private String labelSelector;
  private ResourceEventFilter<R> customResourcePredicate;
  private final ControllerConfiguration<R> original;
  private Duration reconciliationMaxInterval;
  private final LinkedHashMap<String, DependentResourceSpec> namedDependentResourceSpecs;

  private ControllerConfigurationOverrider(ControllerConfiguration<R> original) {
    finalizer = original.getFinalizerName();
    generationAware = original.isGenerationAware();
    namespaces = new HashSet<>(original.getNamespaces());
    retry = original.getRetryConfiguration();
    labelSelector = original.getLabelSelector();
    customResourcePredicate = original.getEventFilter();
    reconciliationMaxInterval = original.reconciliationMaxInterval().orElse(null);
    // make the original specs modifiable
    final var dependentResources = original.getDependentResources();
    namedDependentResourceSpecs = new LinkedHashMap<>(dependentResources.size());
    dependentResources.forEach(drs -> namedDependentResourceSpecs.put(drs.getName(), drs));
    this.original = original;
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
    this.namespaces = ResourceConfiguration.CURRENT_NAMESPACE_ONLY;
    return this;
  }

  public ControllerConfigurationOverrider<R> addingNamespaces(String... namespaces) {
    this.namespaces.addAll(List.of(namespaces));
    return this;
  }

  public ControllerConfigurationOverrider<R> removingNamespaces(String... namespaces) {
    List.of(namespaces).forEach(this.namespaces::remove);
    if (this.namespaces.isEmpty()) {
      this.namespaces = ResourceConfiguration.DEFAULT_NAMESPACES;
    }
    return this;
  }

  public ControllerConfigurationOverrider<R> settingNamespace(String namespace) {
    this.namespaces.clear();
    this.namespaces.add(namespace);
    return this;
  }

  public ControllerConfigurationOverrider<R> watchingAllNamespaces() {
    this.namespaces = ResourceConfiguration.DEFAULT_NAMESPACES;
    return this;
  }

  public ControllerConfigurationOverrider<R> withRetry(RetryConfiguration retry) {
    this.retry = retry;
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

  public ControllerConfigurationOverrider<R> replacingNamedDependentResourceConfig(String name,
      Object dependentResourceConfig) {

    var current = namedDependentResourceSpecs.get(name);
    if (current == null) {
      throw new IllegalArgumentException("Cannot find a DependentResource named: " + name);
    }
    replaceConfig(name, dependentResourceConfig, current);
    return this;
  }

  private void replaceConfig(String name, Object newConfig, DependentResourceSpec<?, ?> current) {
    namedDependentResourceSpecs.put(name,
        new DependentResourceSpec<>(current.getDependentResourceClass(), newConfig, name));
  }

  @SuppressWarnings("unchecked")
  public ControllerConfiguration<R> build() {
    // propagate namespaces if needed
    final List<DependentResourceSpec> newDependentSpecs;
    final var hasModifiedNamespaces = !original.getNamespaces().equals(namespaces);
    newDependentSpecs = namedDependentResourceSpecs.entrySet().stream()
        .map(drsEntry -> {
          final var spec = drsEntry.getValue();

          // if the spec has a config and it's a KubernetesDependentResourceConfig, update the
          // namespaces if needed, otherwise, just return the existing spec
          final Optional<?> maybeConfig = spec.getDependentResourceConfiguration();
          final Class<?> drClass = drsEntry.getValue().getDependentResourceClass();
          return maybeConfig.filter(KubernetesDependentResourceConfig.class::isInstance)
              .map(KubernetesDependentResourceConfig.class::cast)
              .filter(Predicate.not(KubernetesDependentResourceConfig::wereNamespacesConfigured))
              .map(c -> updateSpec(drsEntry.getKey(), drClass, c))
              .orElse(drsEntry.getValue());
        }).collect(Collectors.toUnmodifiableList());

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
        newDependentSpecs);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private DependentResourceSpec<?, ?> updateSpec(String name, Class<?> drClass,
      KubernetesDependentResourceConfig c) {
    return new DependentResourceSpec(drClass, c.setNamespaces(namespaces), name);
  }

  public static <R extends HasMetadata> ControllerConfigurationOverrider<R> override(
      ControllerConfiguration<R> original) {
    return new ControllerConfigurationOverrider<>(original);
  }
}
