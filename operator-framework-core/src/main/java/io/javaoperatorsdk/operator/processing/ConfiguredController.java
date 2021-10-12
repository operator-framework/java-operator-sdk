package io.javaoperatorsdk.operator.processing;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.CustomResourceUtils;
import io.javaoperatorsdk.operator.Metrics.ControllerExecution;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.DefaultEventSourceManager;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

public class ConfiguredController<R extends CustomResource<?, ?>> implements ResourceController<R>,
    Closeable {
  private final ResourceController<R> controller;
  private final ControllerConfiguration<R> configuration;
  private final KubernetesClient kubernetesClient;
  private EventSourceManager eventSourceManager;

  public ConfiguredController(ResourceController<R> controller,
      ControllerConfiguration<R> configuration,
      KubernetesClient kubernetesClient) {
    this.controller = controller;
    this.configuration = configuration;
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public DeleteControl deleteResource(R resource, Context<R> context) {
    return configuration.getConfigurationService().getMetrics().timeControllerExecution(
        new ControllerExecution<>() {
          @Override
          public String name() {
            return "delete";
          }

          @Override
          public String controllerName() {
            return configuration.getName();
          }

          @Override
          public String successTypeName(DeleteControl result) {
            switch (result) {
              case DEFAULT_DELETE:
                return "delete";
              case NO_FINALIZER_REMOVAL:
                return "finalizerNotRemoved";
              default:
                return "unknown";
            }
          }

          @Override
          public DeleteControl execute() {
            return controller.deleteResource(resource, context);
          }
        });
  }

  @Override
  public UpdateControl<R> createOrUpdateResource(R resource, Context<R> context) {
    return configuration.getConfigurationService().getMetrics().timeControllerExecution(
        new ControllerExecution<>() {
          @Override
          public String name() {
            return "createOrUpdate";
          }

          @Override
          public String controllerName() {
            return configuration.getName();
          }

          @Override
          public String successTypeName(UpdateControl<R> result) {
            String successType = "cr";
            if (result.isUpdateStatusSubResource()) {
              successType = "status";
            }
            if (result.isUpdateCustomResourceAndStatusSubResource()) {
              successType = "both";
            }
            return successType;
          }

          @Override
          public UpdateControl<R> execute() {
            return controller.createOrUpdateResource(resource, context);
          }
        });
  }

  @Override
  public void init(EventSourceManager eventSourceManager) {
    throw new UnsupportedOperationException("This method should never be called directly");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ConfiguredController<?> that = (ConfiguredController<?>) o;
    return configuration.getName().equals(that.configuration.getName());
  }

  @Override
  public int hashCode() {
    return configuration.getName().hashCode();
  }

  @Override
  public String toString() {
    return "'" + configuration.getName() + "' Controller";
  }

  public ResourceController<R> getController() {
    return controller;
  }

  public ControllerConfiguration<R> getConfiguration() {
    return configuration;
  }

  public KubernetesClient getClient() {
    return kubernetesClient;
  }

  public MixedOperation<R, KubernetesResourceList<R>, Resource<R>> getCRClient() {
    return kubernetesClient.resources(configuration.getCustomResourceClass());
  }

  /**
   * Registers the specified controller with this operator, overriding its default configuration by
   * the specified one (usually created via
   * {@link io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider#override(ControllerConfiguration)},
   * passing it the controller's original configuration.
   *
   * @throws OperatorException if a problem occurred during the registration process
   */
  public void start() throws OperatorException {
    final Class<R> resClass = configuration.getCustomResourceClass();
    final String controllerName = configuration.getName();
    final var crdName = configuration.getCRDName();
    final var specVersion = "v1";

    // check that the custom resource is known by the cluster if configured that way
    final CustomResourceDefinition crd; // todo: check proper CRD spec version based on config
    if (configuration.getConfigurationService().checkCRDAndValidateLocalModel()) {
      crd =
          kubernetesClient.apiextensions().v1().customResourceDefinitions().withName(crdName).get();
      if (crd == null) {
        throwMissingCRDException(crdName, specVersion, controllerName);
      }

      // Apply validations that are not handled by fabric8
      CustomResourceUtils.assertCustomResource(resClass, crd);
    }

    try {
      eventSourceManager = new DefaultEventSourceManager<>(this);
      controller.init(eventSourceManager);
    } catch (MissingCRDException e) {
      throwMissingCRDException(crdName, specVersion, controllerName);
    }

    if (failOnMissingCurrentNS()) {
      throw new OperatorException(
          "Controller '"
              + controllerName
              + "' is configured to watch the current namespace but it couldn't be inferred from the current configuration.");
    }
  }

  private void throwMissingCRDException(String crdName, String specVersion, String controllerName) {
    throw new MissingCRDException(
        crdName,
        specVersion,
        "'"
            + crdName
            + "' "
            + specVersion
            + " CRD was not found on the cluster, controller '"
            + controllerName
            + "' cannot be registered");
  }

  /**
   * Determines whether we should fail because the current namespace is request as target namespace
   * but is missing
   *
   * @return {@code true} if the current namespace is requested but is missing, {@code false}
   *         otherwise
   */
  private boolean failOnMissingCurrentNS() {
    if (configuration.watchCurrentNamespace()) {
      final var effectiveNamespaces = configuration.getEffectiveNamespaces();
      return effectiveNamespaces.size() == 1
          && effectiveNamespaces.stream().allMatch(Objects::isNull);
    }
    return false;
  }

  public EventSourceManager getEventSourceManager() {
    return eventSourceManager;
  }

  @Override
  public void close() throws IOException {
    if (eventSourceManager != null) {
      eventSourceManager.close();
    }
  }
}
