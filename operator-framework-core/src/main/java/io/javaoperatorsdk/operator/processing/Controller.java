package io.javaoperatorsdk.operator.processing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.monitoring.Metrics;
import io.javaoperatorsdk.operator.api.monitoring.Metrics.ControllerExecution;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ContextInitializer;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

@SuppressWarnings({"unchecked", "rawtypes"})
@Ignore
public class Controller<P extends HasMetadata>
    implements Reconciler<P>, Cleaner<P>, LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(Controller.class);

  private final Reconciler<P> reconciler;
  private final ControllerConfiguration<P> configuration;
  private final KubernetesClient kubernetesClient;
  private final EventSourceManager<P> eventSourceManager;
  private final Map<String, DependentResource> dependents;
  private final boolean contextInitializer;
  private final boolean hasDeleterDependents;
  private final boolean isCleaner;
  private final Metrics metrics;


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

    final var hasDeleterHolder = new boolean[] {false};
    final var specs = configuration.getDependentResources();
    final var size = specs.size();
    if (size == 0) {
      dependents = Collections.emptyMap();
    } else {
      final var dependentsHolder = new HashMap<String, DependentResource>(size);
      specs.forEach((name, drs) -> {
        final var dependent = createAndConfigureFrom(drs, kubernetesClient);
        // check if dependent implements Deleter to record that fact
        if (!hasDeleterHolder[0] && dependent instanceof Deleter) {
          hasDeleterHolder[0] = true;
        }
        dependentsHolder.put(name, dependent);
      });
      dependents = Collections.unmodifiableMap(dependentsHolder);
    }

    hasDeleterDependents = hasDeleterHolder[0];
    isCleaner = reconciler instanceof Cleaner;
  }

  @SuppressWarnings("rawtypes")
  private DependentResource createAndConfigureFrom(DependentResourceSpec spec,
      KubernetesClient client) {
    final var dependentResource =
        ConfigurationServiceProvider.instance().dependentResourceFactory().createFrom(spec);

    if (dependentResource instanceof KubernetesClientAware) {
      ((KubernetesClientAware) dependentResource).setKubernetesClient(client);
    }

    if (dependentResource instanceof DependentResourceConfigurator) {
      final var configurator = (DependentResourceConfigurator) dependentResource;
      spec.getDependentResourceConfiguration().ifPresent(configurator::configureWith);
    }
    return dependentResource;
  }

  private void initContextIfNeeded(P resource, Context<P> context) {
    if (contextInitializer) {
      ((ContextInitializer<P>) reconciler).initContext(resource, context);
    }
  }

  @Override
  public DeleteControl cleanup(P resource, Context<P> context) {
    try {
      return metrics
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
                  initContextIfNeeded(resource, context);
                  if (hasDeleterDependents) {
                    dependents.values().stream()
                        .filter(d -> d instanceof Deleter)
                        .map(Deleter.class::cast)
                        .forEach(deleter -> deleter.delete(resource, context));
                  }
                  if (isCleaner) {
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
            final var result = dependents.entrySet().stream()
                .map(entry -> {
                  final var dependent = entry.getValue();
                  final var name = entry.getKey();
                  ReconcileResult reconcileResult;
                  try {
                    reconcileResult = dependent.reconcile(resource, context);
                  } catch (Exception e) {
                    reconcileResult = ReconcileResult.error(resource, e);
                  }
                  context.managedDependentResourceContext().setReconcileResult(name,
                      reconcileResult);
                  return reconcileResult;
                });

            log.info("Dependents reconciliation:\n{}",
                result.map(r -> "\t" + r.toString()).collect(Collectors.joining("\n")));
            return reconciler.reconcile(resource, context);
          }
        });
  }

  public void initAndRegisterEventSources(EventSourceContext<P> context) {
    dependents.entrySet().stream()
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

      initAndRegisterEventSources(context);

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
    return isCleaner || hasDeleterDependents;
  }

  @SuppressWarnings("rawtypes")
  public Map<String, DependentResource> getDependents() {
    return dependents;
  }
}
