package io.javaoperatorsdk.operator.processing;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.CustomResourceUtils;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.monitoring.Metrics.ControllerExecution;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.dependent.DependentResourceManager;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@SuppressWarnings({"rawtypes", "unchecked"})
public class Controller<R extends HasMetadata> implements Reconciler<R>,
    LifecycleAware, EventSourceInitializer<R> {

  private static final Logger log = LoggerFactory.getLogger(Controller.class);

  private final Reconciler<R> reconciler;
  private final ControllerConfiguration<R> configuration;
  private final KubernetesClient kubernetesClient;
  private final EventSourceManager<R> eventSourceManager;
  private final DependentResourceManager<R> dependents;

  private ConfigurationService configurationService;

  public Controller(Reconciler<R> reconciler,
      ControllerConfiguration<R> configuration,
      KubernetesClient kubernetesClient) {
    this.reconciler = reconciler;
    this.configuration = configuration;
    this.kubernetesClient = kubernetesClient;

    eventSourceManager = new EventSourceManager<>(this);
    dependents = new DependentResourceManager<>(this);
  }

  @Override
  public DeleteControl cleanup(R resource, Context context) {
    dependents.cleanup(resource, context);

    return metrics().timeControllerExecution(
        new ControllerExecution<>() {
          @Override
          public String name() {
            return "cleanup";
          }

          @Override
          public String controllerName() {
            return configuration.getName();
          }

          @Override
          public String successTypeName(DeleteControl deleteControl) {
            return deleteControl.isRemoveFinalizer() ? "delete" : "finalizerNotRemoved";
          }

          @Override
          public DeleteControl execute() {
            return reconciler.cleanup(resource, context);
          }
        });
  }

  @Override
  public UpdateControl<R> reconcile(R resource, Context context) {
    dependents.reconcile(resource, context);

    return metrics().timeControllerExecution(
        new ControllerExecution<>() {
          @Override
          public String name() {
            return "reconcile";
          }

          @Override
          public String controllerName() {
            return configuration.getName();
          }

          @Override
          public String successTypeName(UpdateControl<R> result) {
            String successType = "resource";
            if (result.isUpdateStatus()) {
              successType = "status";
            }
            if (result.isUpdateResourceAndStatus()) {
              successType = "both";
            }
            return successType;
          }

          @Override
          public UpdateControl<R> execute() {
            return reconciler.reconcile(resource, context);
          }
        });
  }


  private Metrics metrics() {
    final var metrics = configurationService().getMetrics();
    return metrics != null ? metrics : Metrics.NOOP;
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<R> context) {
    final var dependentSources = dependents.prepareEventSources(context);
    List<EventSource> sources = new LinkedList<>(dependentSources);

    // add manually defined event sources
    if (reconciler instanceof EventSourceInitializer) {
      sources.addAll(((EventSourceInitializer<R>) reconciler).prepareEventSources(context));
    }
    return sources;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Controller<?> that = (Controller<?>) o;
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

  public Reconciler<R> getReconciler() {
    return reconciler;
  }

  public ControllerConfiguration<R> getConfiguration() {
    return configuration;
  }

  public KubernetesClient getClient() {
    return kubernetesClient;
  }

  public MixedOperation<R, KubernetesResourceList<R>, Resource<R>> getCRClient() {
    return kubernetesClient.resources(configuration.getResourceClass());
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
    final Class<R> resClass = configuration.getResourceClass();
    final String controllerName = configuration.getName();
    final var crdName = configuration.getResourceTypeName();
    final var specVersion = "v1";
    log.info("Starting '{}' controller for reconciler: {}, resource: {}", controllerName,
        reconciler.getClass().getCanonicalName(), resClass.getCanonicalName());
    try {
      // check that the custom resource is known by the cluster if configured that way
      final CustomResourceDefinition crd; // todo: check proper CRD spec version based on config
      if (configurationService().checkCRDAndValidateLocalModel()
          && CustomResource.class.isAssignableFrom(resClass)) {
        crd = kubernetesClient.apiextensions().v1().customResourceDefinitions().withName(crdName)
            .get();
        if (crd == null) {
          throwMissingCRDException(crdName, specVersion, controllerName);
        }

        // Apply validations that are not handled by fabric8
        CustomResourceUtils.assertCustomResource(resClass, crd);
      }

      if (failOnMissingCurrentNS()) {
        throw new OperatorException(
            "Controller '"
                + controllerName
                + "' is configured to watch the current namespace but it couldn't be inferred from the current configuration.");
      }

      final var context = new EventSourceContext<>(
          eventSourceManager.getControllerResourceEventSource().getResourceCache(),
          configurationService(), kubernetesClient);

      dependents.injectInto(context);
      prepareEventSources(context).forEach(eventSourceManager::registerEventSource);

      eventSourceManager.start();

      log.info("'{}' controller started, pending event sources initialization", controllerName);
    } catch (MissingCRDException e) {
      throwMissingCRDException(crdName, specVersion, controllerName);
    }
  }

  private ConfigurationService configurationService() {
    if (configurationService == null) {
      configurationService = configuration.getConfigurationService();
      // make sure we always have a default configuration service
      if (configurationService == null) {
        // we shouldn't need to register the configuration with the default service
        configurationService = new BaseConfigurationService(Version.UNKNOWN) {
          @Override
          public boolean checkCRDAndValidateLocalModel() {
            return false;
          }
        };
      }
    }
    return configurationService;
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

  public EventSourceManager<R> getEventSourceManager() {
    return eventSourceManager;
  }

  public void stop() {
    if (eventSourceManager != null) {
      eventSourceManager.stop();
    }
  }
}
