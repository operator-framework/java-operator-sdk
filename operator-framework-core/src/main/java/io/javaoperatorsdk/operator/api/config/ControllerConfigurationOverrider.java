package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

public class ControllerConfigurationOverrider<R extends HasMetadata> {

  private String finalizer;
  private boolean generationAware;
  private final Set<String> namespaces;
  private RetryConfiguration retry;
  private String labelSelector;
  private ResourceEventFilter<R> customResourcePredicate;
  private final ControllerConfiguration<R> original;
  private Duration reconciliationMaxInterval;
  private List<DependentResourceSpec> dependentResourceSpecs;

  private ControllerConfigurationOverrider(ControllerConfiguration<R> original) {
    finalizer = original.getFinalizer();
    generationAware = original.isGenerationAware();
    namespaces = new HashSet<>(original.getNamespaces());
    retry = original.getRetryConfiguration();
    labelSelector = original.getLabelSelector();
    customResourcePredicate = original.getEventFilter();
    reconciliationMaxInterval = original.reconciliationMaxInterval().orElse(null);
    dependentResourceSpecs = original.getDependentResources();
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

  /**
   * If a {@link DependentResourceSpec} already exists with the same dependentResourceClass it will
   * be replaced. Otherwise, an exception is thrown;
   *
   * @param dependentResourceSpec to add or replace
   */
  public void replaceDependentResourceConfig(DependentResourceSpec dependentResourceSpec) {
    var currentConfig =
        findConfigForDependentResourceClass(dependentResourceSpec.getDependentResourceClass());
    // .orElseThrow(
    // () -> new IllegalStateException(
    // "No config found for class: "
    // + dependentResourceSpec.getDependentResourceClass()));
    // dependentResourceSpecs.remove(currentConfig);
    // dependentResourceSpecs.add(dependentResourceSpec);
  }

  public void addNewDependentResourceConfig(DependentResourceSpec dependentResourceSpec) {
    var currentConfig =
        findConfigForDependentResourceClass(dependentResourceSpec.getDependentResourceClass());
    if (currentConfig.isPresent()) {
      throw new IllegalStateException(
          "Config already present for class: "
              + dependentResourceSpec.getDependentResourceClass());
    }
    dependentResourceSpecs.add(dependentResourceSpec);
  }

  private Optional<DependentResourceSpec> findConfigForDependentResourceClass(
      Class<? extends DependentResource> dependentResourceClass) {
    return dependentResourceSpecs.stream()
        .filter(dc -> dc.getDependentResourceClass().equals(dependentResourceClass))
        .findFirst();
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
        original.getConfigurationService(),
        dependentResourceSpecs);
  }

  public static <R extends HasMetadata> ControllerConfigurationOverrider<R> override(
      ControllerConfiguration<R> original) {
    return new ControllerConfigurationOverrider<>(original);
  }
}
