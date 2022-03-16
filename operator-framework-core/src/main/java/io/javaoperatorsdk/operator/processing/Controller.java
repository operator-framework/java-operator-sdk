package io.javaoperatorsdk.operator.processing;

import java.util.LinkedList;
import java.util.List;

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
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.monitoring.Metrics.ControllerExecution;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.DependentResourceManager;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@SuppressWarnings({"unchecked"})
@Ignore
public class Controller<P extends HasMetadata> implements Reconciler<P>, Cleaner<P>,
    LifecycleAware, EventSourceInitializer<P> {

  private static final Logger log = LoggerFactory.getLogger(Controller.class);

  private final Reconciler<P> reconciler;
  private final ControllerConfiguration<P> configuration;
  private final KubernetesClient kubernetesClient;
  private final EventSourceManager<P> eventSourceManager;
  private final DependentResourceManager<P> dependents;

  public Controller(Reconciler<P> reconciler,
      ControllerConfiguration<P> configuration,
      KubernetesClient kubernetesClient) {
    this.reconciler = reconciler;
    this.configuration = configuration;
    this.kubernetesClient = kubernetesClient;

    eventSourceManager = new EventSourceManager<>(this);
    dependents = new DependentResourceManager<>(this);
  }

  @Override
  public DeleteControl cleanup(P resource, Context<P> context) {
    try {
      return metrics()
          .timeControllerExecution(
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
                  dependents.cleanup(resource, context);
                  if (reconciler instanceof Cleaner) {
                    return ((Cleaner<P>) reconciler).cleanup(resource, context);
                  } else {
                    return DeleteControl.defaultDelete();
                  }
                }
              });
    } catch (Exception e) {
      throw new OperatorException(e);
    }
  }

  @Override
  public UpdateControl<P> reconcile(P resource, Context<P> context) throws Exception {
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
          public String successTypeName(UpdateControl<P> result) {
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
          public UpdateControl<P> execute() throws Exception {
            return reconciler.reconcile(resource, context);
          }
        });
  }


  private Metrics metrics() {
    final var metrics = ConfigurationServiceProvider.instance().getMetrics();
    return metrics != null ? metrics : Metrics.NOOP;
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<P> context) {
    final var dependentSources = dependents.prepareEventSources(context);
    List<EventSource> sources = new LinkedList<>(dependentSources);

    // add manually defined event sources
    if (reconciler instanceof EventSourceInitializer) {
      sources.addAll(((EventSourceInitializer<P>) reconciler).prepareEventSources(context));
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

  public Reconciler<P> getReconciler() {
    return reconciler;
  }

  public ControllerConfiguration<P> getConfiguration() {
    return configuration;
  }

  public KubernetesClient getClient() {
    return kubernetesClient;
  }

  public MixedOperation<P, KubernetesResourceList<P>, Resource<P>> getCRClient() {
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
    final Class<P> resClass = configuration.getResourceClass();
    final String controllerName = configuration.getName();
    final var crdName = configuration.getResourceTypeName();
    final var specVersion = "v1";
    log.info("Starting '{}' controller for reconciler: {}, resource: {}", controllerName,
        reconciler.getClass().getCanonicalName(), resClass.getCanonicalName());

    // fail early if we're missing the current namespace information
    failOnMissingCurrentNS();

    try {
      // check that the custom resource is known by the cluster if configured that way
      final CustomResourceDefinition crd; // todo: check proper CRD spec version based on config
      if (ConfigurationServiceProvider.instance().checkCRDAndValidateLocalModel()
          && CustomResource.class.isAssignableFrom(resClass)) {
        crd = kubernetesClient.apiextensions().v1().customResourceDefinitions().withName(crdName)
            .get();
        if (crd == null) {
          throwMissingCRDException(crdName, specVersion, controllerName);
        }

        // Apply validations that are not handled by fabric8
        CustomResourceUtils.assertCustomResource(resClass, crd);
      }

      final var context = new EventSourceContext<>(
          eventSourceManager.getControllerResourceEventSource(), configuration, kubernetesClient);

      prepareEventSources(context).forEach(eventSourceManager::registerEventSource);

      eventSourceManager.start();

      log.info("'{}' controller started, pending event sources initialization", controllerName);
    } catch (MissingCRDException e) {
      throwMissingCRDException(crdName, specVersion, controllerName);
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
   * Throws an {@link OperatorException} if the controller is configured to watch the current
   * namespace but it's absent from the configuration.
   */
  private void failOnMissingCurrentNS() {
    try {
      configuration.getEffectiveNamespaces();
    } catch (OperatorException e) {
      throw new OperatorException(
          "Controller '"
              + configuration.getName()
              + "' is configured to watch the current namespace but it couldn't be inferred from the current configuration.");
    }
  }

  public EventSourceManager<P> getEventSourceManager() {
    return eventSourceManager;
  }

  public void stop() {
    if (eventSourceManager != null) {
      eventSourceManager.stop();
    }
  }

  public boolean useFinalizer() {
    return reconciler instanceof Cleaner || dependents.requiresCleanup();
  }

  @SuppressWarnings("rawtypes")
  public List<DependentResource> getDependents() {
    return dependents.getDependents();
  }
}
