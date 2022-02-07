package io.javaoperatorsdk.operator.processing.dependent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

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
public class DependentResourceManager<P extends HasMetadata> implements EventSourceInitializer<P>,
    EventSourceContextInjector, Reconciler<P> {
  private final Reconciler<P> reconciler;
  private final ControllerConfiguration<P> configuration;
  private List<DependentResourceController> dependents;


  public DependentResourceManager(Controller<P> controller) {
    this.reconciler = controller.getReconciler();
    this.configuration = controller.getConfiguration();
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<P> context) {
    final List<DependentResourceConfiguration> configured = configuration.getDependentResources();
    dependents = new ArrayList<>(configured.size());

    List<EventSource> sources = new ArrayList<>(configured.size() + 5);
    configured.forEach(dependent -> {
      final var dependentResourceController = from(dependent);
      dependents.add(dependentResourceController);
      dependentResourceController.initEventSource(context)
          .ifPresent(es -> sources.add((EventSource) es));

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
  public UpdateControl<P> reconcile(P resource, Context context) {
    initContextIfNeeded(resource, context);
    dependents.forEach(dependent -> dependent.reconcile(resource, context));
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(P resource, Context context) {
    initContextIfNeeded(resource, context);
    dependents.forEach(dependent -> dependent.delete(resource, context));
    return Reconciler.super.cleanup(resource, context);
  }


  private void initContextIfNeeded(P resource, Context context) {
    if (reconciler instanceof ContextInitializer) {
      final var initializer = (ContextInitializer<P>) reconciler;
      initializer.initContext(resource, context);
    }
  }

  private DependentResourceController from(DependentResourceConfiguration config) {
    try {
      final var dependentResource =
          (DependentResource) config.getDependentResourceClass().getConstructor()
              .newInstance();
      if (config instanceof KubernetesDependentResourceConfiguration) {
        return new KubernetesDependentResourceController(dependentResource,
            (KubernetesDependentResourceConfiguration) config);
      } else {
        return new DependentResourceController(dependentResource, config);
      }
    } catch (NoSuchMethodException | InvocationTargetException | InstantiationException
        | IllegalAccessException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
