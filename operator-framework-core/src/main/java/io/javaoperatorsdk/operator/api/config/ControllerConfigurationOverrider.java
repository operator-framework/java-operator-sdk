package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

@SuppressWarnings({"unused"})
public class ControllerConfigurationOverrider<R extends HasMetadata> {

  private String finalizer;
  private boolean generationAware;
  private final Set<String> namespaces;
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

  public ControllerConfigurationOverrider<R> withCurrentNamespace() {
    namespaces.clear();
    return this;
  }

  public ControllerConfigurationOverrider<R> addingNamespaces(String... namespaces) {
    this.namespaces.addAll(List.of(namespaces));
    return this;
  }

  public ControllerConfigurationOverrider<R> removingNamespaces(String... namespaces) {
    List.of(namespaces).forEach(this.namespaces::remove);
    return this;
  }

  public ControllerConfigurationOverrider<R> settingNamespace(String namespace) {
    this.namespaces.clear();
    this.namespaces.add(namespace);
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

  public ControllerConfiguration<R> build() {
    // propagate namespaces if needed
    final List<DependentResourceSpec<?, ?>> newDependentSpecs;
    if (!original.getNamespaces().equals(namespaces)) {
      newDependentSpecs = namedDependentResourceSpecs.entrySet().stream()
          .filter(drsEntry -> drsEntry.getValue().getDependentResourceConfiguration()
              .map(c -> c instanceof KubernetesDependentResourceConfig)
              .orElse(false))
          .map(drsEntry -> {
            final var spec = drsEntry.getValue();
            final var existing =
                (KubernetesDependentResourceConfig) spec.getDependentResourceConfiguration().get();

            // only use the dependent's namespaces were not explicitly configured
            if (!existing.wereNamespacesConfigured()) {
              replaceConfig(drsEntry.getKey(), existing.setNamespaces(namespaces), spec);
            }
            return spec;
          }).collect(Collectors.toUnmodifiableList());
    } else {
      newDependentSpecs = namedDependentResourceSpecs.values().stream()
          .collect(Collectors.toUnmodifiableList());
    }

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

  public static <R extends HasMetadata> ControllerConfigurationOverrider<R> override(
      ControllerConfiguration<R> original) {
    return new ControllerConfigurationOverrider<>(original);
  }
}
