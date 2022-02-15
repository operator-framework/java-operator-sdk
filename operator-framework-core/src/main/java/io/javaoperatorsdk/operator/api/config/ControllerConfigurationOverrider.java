package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfig;
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
  private List<DependentResourceConfig> dependentResourceConfigs;

  private ControllerConfigurationOverrider(ControllerConfiguration<R> original) {
    finalizer = original.getFinalizer();
    generationAware = original.isGenerationAware();
    namespaces = new HashSet<>(original.getNamespaces());
    retry = original.getRetryConfiguration();
    labelSelector = original.getLabelSelector();
    customResourcePredicate = original.getEventFilter();
    reconciliationMaxInterval = original.reconciliationMaxInterval().orElse(null);
    dependentResourceConfigs = original.getDependentResources();
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
   * If a {@link DependentResourceConfig} already exists with the same dependentResourceClass it
   * will be replaced. Otherwise, an exception is thrown;
   *
   * @param dependentResourceConfig to add or replace
   */
  public void replaceDependentResourceConfig(DependentResourceConfig dependentResourceConfig) {
    var currentConfig =
        findConfigForDependentResourceClass(dependentResourceConfig.getDependentResourceClass())
            .orElseThrow(
                () -> new IllegalStateException(
                    "No config found for class: "
                        + dependentResourceConfig.getDependentResourceClass()));
    dependentResourceConfigs.remove(currentConfig);
    dependentResourceConfigs.add(dependentResourceConfig);
  }

  public void addNewDependentResourceConfig(DependentResourceConfig dependentResourceConfig) {
    var currentConfig =
        findConfigForDependentResourceClass(dependentResourceConfig.getDependentResourceClass());
    if (currentConfig.isPresent()) {
      throw new IllegalStateException(
          "Config already present for class: "
              + dependentResourceConfig.getDependentResourceClass());
    }
    dependentResourceConfigs.add(dependentResourceConfig);
  }

  private Optional<DependentResourceConfig> findConfigForDependentResourceClass(
      Class<? extends DependentResource> dependentResourceClass) {
    return dependentResourceConfigs.stream()
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
        dependentResourceConfigs);
  }

  public static <R extends HasMetadata> ControllerConfigurationOverrider<R> override(
      ControllerConfiguration<R> original) {
    return new ControllerConfigurationOverrider<>(original);
  }
}
