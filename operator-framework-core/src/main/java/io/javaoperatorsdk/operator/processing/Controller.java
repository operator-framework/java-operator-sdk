package io.javaoperatorsdk.operator.processing;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.CustomResourceUtils;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.monitoring.Metrics.ControllerExecution;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.source.AbstractResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.DependentResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceWrapper;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEventFilter;

public class Controller<R extends HasMetadata> implements Reconciler<R>,
    LifecycleAware, EventSourceInitializer<R> {

  private static final Logger log = LoggerFactory.getLogger(Controller.class);
  private final Reconciler<R> reconciler;
  private final ControllerConfiguration<R> configuration;
  private final KubernetesClient kubernetesClient;
  private final EventSourceManager<R> eventSourceManager;
  private final AtomicBoolean started = new AtomicBoolean(false);

  public Controller(Reconciler<R> reconciler,
      ControllerConfiguration<R> configuration,
      KubernetesClient kubernetesClient) {
    this.reconciler = reconciler;
    this.configuration = configuration;
    this.kubernetesClient = kubernetesClient;

    eventSourceManager = new EventSourceManager<>(this);
    prepareEventSources(eventSourceManager,
        configuration.getConfigurationService().getResourceCloner());
  }

  private void waitUntilStarted() {
    if (!started.get()) {
      AtomicInteger count = new AtomicInteger(0);
      final var waitTime = 50;
      while (!started.get()) {
        try {
          count.getAndIncrement();
          Thread.sleep(waitTime);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      log.info("Waited {}ms for controller '{}' to finish initializing", count.get() * waitTime,
          configuration.getName());
    }
  }

  @Override
  public DeleteControl cleanup(R resource, Context<R> context) {
    waitUntilStarted();
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
  public UpdateControl<R> reconcile(R resource, Context<R> context) {
    waitUntilStarted();
    final var metrics = configuration.getConfigurationService().getMetrics();

    configuration.getDependentResources().forEach(dependent -> {
      final var conf = dependent.getConfiguration();

      if (!conf.creatable() && !conf.updatable()) {
        return;
      }

      var dependentResource = dependent.fetchFor(resource);
      if (conf.creatable() && dependentResource == null) {
        // we need to create the dependent
        dependentResource = dependent.buildFor(resource);
        createOrReplaceDependent(resource, conf, dependentResource, "Creating");
      } else if (conf.updatable()) {
        dependentResource = dependent.update(dependentResource, resource);
        createOrReplaceDependent(resource, conf, dependentResource, "Updating");
      } else {
        logOperationInfo(resource, conf, dependentResource, "Ignoring");
      }
    });

    return metrics.timeControllerExecution(
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

  private void createOrReplaceDependent(R resource, DependentResourceConfiguration conf,
      HasMetadata dependentResource, String operationDescription) {
    addOwnerReferenceIfNeeded(resource, conf, dependentResource);
    logOperationInfo(resource, conf, dependentResource, operationDescription);
    // send the changes to the cluster
    // todo: would be nice to be able to update informer directly…
    // todo: add metrics timing for dependent resource
    kubernetesClient.resource(dependentResource).createOrReplace();
  }

  private void logOperationInfo(R resource, DependentResourceConfiguration conf,
      HasMetadata dependentResource, String operationDescription) {
    log.info(operationDescription + " '{}' {} dependent in namespace {} for '{}' {}",
        dependentResource.getMetadata().getName(), conf.getResourceTypeName(),
        dependentResource.getMetadata().getNamespace(), resource.getMetadata().getName(),
        configuration.getResourceTypeName());
  }

  private void addOwnerReferenceIfNeeded(R resource, DependentResourceConfiguration conf,
      HasMetadata dependentResource) {
    // todo: use owner reference support from fabric8 client to avoid adding several times the same
    // reference
    if (conf.owned()) {
      final var metadata = resource.getMetadata();
      final var updatedMetadata = new ObjectMetaBuilder(dependentResource.getMetadata())
          .addNewOwnerReference()
          .withUid(metadata.getUid())
          .withApiVersion(resource.getApiVersion())
          .withName(metadata.getName())
          .withKind(resource.getKind())
          .endOwnerReference().build();
      dependentResource.setMetadata(updatedMetadata);
    }
  }

  @Override
  public void prepareEventSources(EventSourceRegistry<R> eventSourceRegistry, Cloner cloner) {
    configuration.getDependentResources().forEach(dependent -> {
      final var dependentConfiguration = dependent.getConfiguration();
      new AbstractResourceEventSource<>(dependentConfiguration,
          kubernetesClient.resources(dependentConfiguration.getResourceClass()), cloner,
          eventSourceRegistry) {
        @Override
        protected ResourceEventFilter initFilter(ResourceConfiguration configuration) {
          return configuration.getEventFilter();
        }

        @Override
        protected EventSourceWrapper wrapEventSource(
            FilterWatchListDeletable filteredBySelectorClient, Cloner cloner) {
          final var dependentSource = new DependentResourceEventSource(filteredBySelectorClient,
              cloner, dependentConfiguration);
          // make sure we're set to receive events
          eventSourceRegistry.registerEventSource(dependentSource);
          dependent.setSource(dependentSource);
          return dependentSource;
        }
      };
    });

    if (reconciler instanceof EventSourceInitializer) {
      ((EventSourceInitializer<R>) reconciler).prepareEventSources(eventSourceManager, cloner);
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
    final var configurationService = configuration.getConfigurationService();
    try {
      // check that the custom resource is known by the cluster if configured that way
      final CustomResourceDefinition crd; // todo: check proper CRD spec version based on config
      if (configurationService.checkCRDAndValidateLocalModel()) {
        crd =
            kubernetesClient.apiextensions().v1().customResourceDefinitions().withName(crdName)
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
      eventSourceManager.start();
      started.set(true);
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
    started.set(false);
  }

  public EventSourceRegistry<R> getEventSourceRegistry() {
    return eventSourceManager;
  }
}
