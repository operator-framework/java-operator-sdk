package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

@SuppressWarnings({"rawtypes", "unchecked", "unused"})
public class ControllerConfigurationOverrider<R extends HasMetadata> {

  private String finalizer;
  private boolean generationAware;
  private final Set<String> namespaces;
  private RetryConfiguration retry;
  private String labelSelector;
  private ResourceEventFilter<R> customResourcePredicate;
  private final ControllerConfiguration<R> original;
  private Duration reconciliationMaxInterval;
  private final List<DependentResourceSpec<?, ?>> namedDependentResourceSpecs;

  private ControllerConfigurationOverrider(ControllerConfiguration<R> original) {
    finalizer = original.getFinalizerName();
    generationAware = original.isGenerationAware();
    namespaces = new HashSet<>(original.getNamespaces());
    retry = original.getRetryConfiguration();
    labelSelector = original.getLabelSelector();
    customResourcePredicate = original.getEventFilter();
    reconciliationMaxInterval = original.reconciliationMaxInterval().orElse(null);
    // make the original specs modifiable
    namedDependentResourceSpecs = new ArrayList<>(original.getDependentResources());
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

    var namedRDS = namedDependentResourceSpecs.stream().filter(drs -> drs.getName().equals(name))
        .collect(Collectors.toList());
    if (namedRDS.size() > 1) {
      throw new IllegalStateException(
          "More than 1 NamedDependentResourceSpec for for name: " + name);
    }
    if (namedRDS.isEmpty()) {
      throw new IllegalArgumentException("Cannot find a DependentResource named: " + name);
    }
    namedDependentResourceSpecs.remove(namedRDS.get(0));
    namedDependentResourceSpecs.add(new DependentResourceSpec<>(
        namedRDS.get(0).getDependentResourceClass(), dependentResourceConfig, name));
    return this;
  }

  public ControllerConfiguration<R> build() {
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
        namedDependentResourceSpecs);
  }

  public static <R extends HasMetadata> ControllerConfigurationOverrider<R> override(
      ControllerConfiguration<R> original) {
    return new ControllerConfigurationOverrider<>(original);
  }
}
