package io.javaoperatorsdk.operator.processing.dependent;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ContextInitializer;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContextInjector;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@SuppressWarnings({"rawtypes", "unchecked"})
@Ignore
public class DependentResourceManager<R extends HasMetadata> implements EventSourceInitializer<R>,
    EventSourceContextInjector, Reconciler<R> {

  private static final Logger log = LoggerFactory.getLogger(DependentResourceManager.class);

  private final Reconciler<R> reconciler;
  private final ControllerConfiguration<R> configuration;
  private List<DependentResourceController> dependents;


  public DependentResourceManager(Controller<R> controller) {
    this.reconciler = controller.getReconciler();
    this.configuration = controller.getConfiguration();
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<R> context) {
    final List<DependentResourceConfiguration> configured = configuration.getDependentResources();
    dependents = new ArrayList<>(configured.size());

    List<EventSource> sources = new ArrayList<>(configured.size() + 5);
    configured.forEach(dependent -> {
      final var dependentResourceController = configuration.dependentFactory().from(dependent);
      dependents.add(dependentResourceController);
      sources.add(dependentResourceController.initEventSource(context));
    });

    return sources;
  }

  @Override
  public void injectInto(EventSourceContext context) {
    if (reconciler instanceof EventSourceContextInjector) {
      EventSourceContextInjector injector = (EventSourceContextInjector) reconciler;
      injector.injectInto(context);
    }
  }

  @Override
  public UpdateControl<R> reconcile(R resource, Context context) {
    initContextIfNeeded(resource, context);

    dependents.stream().forEach(dependent -> {
      var actual = dependent.getFor(resource, context);
      if (actual == null || !dependent.match(actual, resource, context)) {
        final var desired = dependent.desired(resource, context);
        if (desired != null) {
          createOrReplaceDependent(resource, context, dependent, desired, "Reconciling");
        }
      }
    });

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(R resource, Context context) {
    initContextIfNeeded(resource, context);

    dependents.stream()
        .filter(DependentResourceController::deletable)
        .forEach(dependent -> {
          var dependentResource = dependent.getFor(resource, context);
          if (dependentResource != null) {
            dependent.delete(dependentResource, resource, context);
            logOperationInfo(resource, dependent, dependentResource, "Deleting");
          } else {
            log.info("Ignoring already deleted {} for '{}' {}",
                dependent.getResourceType().getName(),
                resource.getMetadata().getName(),
                configuration.getResourceTypeName());
          }
        });

    return Reconciler.super.cleanup(resource, context);
  }

  private void createOrReplaceDependent(R primaryResource,
      Context context, DependentResourceController dependentController,
      Object dependentResource, String operationDescription) {
    // add owner reference if needed
    if (dependentResource instanceof HasMetadata
        && ((KubernetesDependentResourceController) dependentController).owned()) {
      ((HasMetadata) dependentResource).addOwnerReference(primaryResource);
    }

    logOperationInfo(primaryResource, dependentController, dependentResource, operationDescription);

    // commit the changes
    // todo: add metrics timing for dependent resource
    dependentController.createOrReplace(dependentResource, context);
  }

  private void logOperationInfo(R resource, DependentResourceController dependent,
      Object dependentResource, String operationDescription) {
    if (log.isInfoEnabled()) {
      log.info("{} {} for '{}' {}", operationDescription,
          dependent.descriptionFor(dependentResource),
          resource.getMetadata().getName(),
          configuration.getResourceTypeName());
    }
  }

  private void initContextIfNeeded(R resource, Context context) {
    if (reconciler instanceof ContextInitializer) {
      final var initializer = (ContextInitializer<R>) reconciler;
      initializer.initContext(resource, context);
    }
  }
}
