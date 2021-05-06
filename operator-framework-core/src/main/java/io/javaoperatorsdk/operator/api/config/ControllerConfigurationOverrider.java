package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ControllerConfigurationOverrider<R extends CustomResource> {

  private String finalizer;
  private boolean generationAware;
  private Set<String> namespaces;
  private RetryConfiguration retry;
  private final ControllerConfiguration<R> original;

  private ControllerConfigurationOverrider(ControllerConfiguration<R> original) {
    finalizer = original.getFinalizer();
    generationAware = original.isGenerationAware();
    namespaces = new HashSet<>(original.getNamespaces());
    retry = original.getRetryConfiguration();
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
    this.namespaces.removeAll(List.of(namespaces));
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

  public ControllerConfiguration<R> build() {
    return new AbstractControllerConfiguration<R>(
        original.getAssociatedControllerClassName(),
        original.getName(),
        original.getCRDName(),
        finalizer,
        generationAware,
        namespaces,
        retry) {
      @Override
      public Class<R> getCustomResourceClass() {
        return original.getCustomResourceClass();
      }

      @Override
      public ConfigurationService getConfigurationService() {
        return original.getConfigurationService();
      }
    };
  }

  public static <R extends CustomResource> ControllerConfigurationOverrider<R> override(
      ControllerConfiguration<R> original) {
    return new ControllerConfigurationOverrider<>(original);
  }
}
