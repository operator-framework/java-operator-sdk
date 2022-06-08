package io.javaoperatorsdk.operator.processing;

import java.util.Optional;
import java.util.Set;

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
import io.javaoperatorsdk.operator.RegisteredController;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.monitoring.Metrics.ControllerExecution;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ContextInitializer;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DefaultManagedDependentResourceContext;
import io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflow;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@SuppressWarnings({"unchecked", "rawtypes"})
@Ignore
public class Controller<P extends HasMetadata>
    implements Reconciler<P>, Cleaner<P>, LifecycleAware, RegisteredController<P> {

  private static final Logger log = LoggerFactory.getLogger(Controller.class);

  private final Reconciler<P> reconciler;
  private final ControllerConfiguration<P> configuration;
  private final KubernetesClient kubernetesClient;
  private final EventSourceManager<P> eventSourceManager;
  private final boolean contextInitializer;
  private final boolean isCleaner;
  private final Metrics metrics;
  private final ManagedWorkflow<P> managedWorkflow;

  public Controller(Reconciler<P> reconciler,
      ControllerConfiguration<P> configuration,
      KubernetesClient kubernetesClient) {
    this.reconciler = reconciler;
    this.configuration = configuration;
    this.kubernetesClient = kubernetesClient;
    this.metrics = Optional.ofNullable(ConfigurationServiceProvider.instance().getMetrics())
        .orElse(Metrics.NOOP);
    contextInitializer = reconciler instanceof ContextInitializer;
    eventSourceManager = new EventSourceManager<>(this);
    isCleaner = reconciler instanceof Cleaner;
    managedWorkflow =
        ManagedWorkflow.workflowFor(kubernetesClient, configuration.getDependentResources());
  }

  @Override
  public UpdateControl<P> reconcile(P resource, Context<P> context) throws Exception {
    return metrics.timeControllerExecution(
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
            initContextIfNeeded(resource, context);
            if (!managedWorkflow.isEmptyWorkflow()) {
              var res = managedWorkflow.reconcile(resource, context);
              ((DefaultManagedDependentResourceContext) context.managedDependentResourceContext())
                  .setWorkflowExecutionResult(res);
              res.throwAggregateExceptionIfErrorsPresent();
            }
            return reconciler.reconcile(resource, context);
          }
        });
  }

  @Override
  public DeleteControl cleanup(P resource, Context<P> context) {
    try {
      return metrics.timeControllerExecution(
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
              initContextIfNeeded(resource, context);
              WorkflowCleanupResult workflowCleanupResult = null;
              if (managedWorkflow.isCleaner()) {
                workflowCleanupResult = managedWorkflow.cleanup(resource, context);
                ((DefaultManagedDependentResourceContext) context.managedDependentResourceContext())
                    .setWorkflowCleanupResult(workflowCleanupResult);
                workflowCleanupResult.throwAggregateExceptionIfErrorsPresent();
              }
              if (isCleaner) {
                var cleanupResult = ((Cleaner<P>) reconciler).cleanup(resource, context);
                if (!cleanupResult.isRemoveFinalizer()) {
                  return cleanupResult;
                } else {
                  // this means there is no reschedule
                  return workflowCleanupResultToDefaultDelete(workflowCleanupResult);
                }
              } else {
                return workflowCleanupResultToDefaultDelete(workflowCleanupResult);
              }
            }
          });
    } catch (Exception e) {
      throw new OperatorException(e);
    }
  }

  private DeleteControl workflowCleanupResultToDefaultDelete(
      WorkflowCleanupResult workflowCleanupResult) {
    if (workflowCleanupResult == null) {
      return DeleteControl.defaultDelete();
    } else {
      return workflowCleanupResult.allPostConditionsMet() ? DeleteControl.defaultDelete()
          : DeleteControl.noFinalizerRemoval();
    }
  }

  private void initContextIfNeeded(P resource, Context<P> context) {
    if (contextInitializer) {
      ((ContextInitializer<P>) reconciler).initContext(resource, context);
    }
  }

  public void initAndRegisterEventSources(EventSourceContext<P> context) {
    managedWorkflow
        .getDependentResourcesByName().entrySet().stream()
        .filter(drEntry -> drEntry.getValue() instanceof EventSourceProvider)
        .forEach(drEntry -> {
          final var provider = (EventSourceProvider) drEntry.getValue();
          final var source = provider.initEventSource(context);
          eventSourceManager.registerEventSource(drEntry.getKey(), source);
        });

    // add manually defined event sources
    if (reconciler instanceof EventSourceInitializer) {
      final var provider = (EventSourceInitializer<P>) this.reconciler;
      final var ownSources = provider.prepareEventSources(context);
      ownSources.forEach(eventSourceManager::registerEventSource);
    }
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
      validateCRDWithLocalModelIfRequired(resClass, controllerName, crdName, specVersion);
      final var context = new EventSourceContext<>(
          eventSourceManager.getControllerResourceEventSource(), configuration, kubernetesClient);

      initAndRegisterEventSources(context);
      eventSourceManager.start();
      log.info("'{}' controller started, pending event sources initialization", controllerName);
    } catch (MissingCRDException e) {
      stop();
      throwMissingCRDException(crdName, specVersion, controllerName);
    }
  }

  private void validateCRDWithLocalModelIfRequired(Class<P> resClass, String controllerName,
      String crdName, String specVersion) {
    final CustomResourceDefinition crd;
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
  }

  public void changeNamespaces(Set<String> namespaces) {
    if (namespaces.contains(Constants.WATCH_ALL_NAMESPACES)
        || namespaces.contains(WATCH_CURRENT_NAMESPACE)) {
      throw new OperatorException("Unexpected value in target namespaces: " + namespaces);
    }
    eventSourceManager.changeNamespaces(namespaces);
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
    return isCleaner || managedWorkflow.isCleaner();
  }
}
