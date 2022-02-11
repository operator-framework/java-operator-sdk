package io.javaoperatorsdk.operator.processing.dependent;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
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
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@SuppressWarnings({"rawtypes", "unchecked"})
@Ignore
public class DependentResourceManager<P extends HasMetadata> implements EventSourceInitializer<P>,
    EventSourceContextInjector, Reconciler<P> {
  private final Reconciler<P> reconciler;
  private final ControllerConfiguration<P> controllerConfiguration;
  private List<DependentResource> dependents;


  public DependentResourceManager(Controller<P> controller, KubernetesClient kubernetesClient) {
    this.reconciler = controller.getReconciler();
    this.controllerConfiguration = controller.getConfiguration();
    initDependentResourceControllers(kubernetesClient);
  }

  private void initDependentResourceControllers(KubernetesClient kubernetesClient) {
    final List<DependentResourceConfiguration> dependentResourceConfigurations =
        controllerConfiguration.getDependentResources();
    dependents = new ArrayList<>(dependentResourceConfigurations.size());
    dependentResourceConfigurations.forEach(dependent -> {
      final var dependentResourceController = from(dependent, kubernetesClient);
      dependents.add(dependentResourceController);
    });
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<P> context) {
    List<EventSource> sources = new ArrayList<>();
    dependents.forEach(dependent -> {
      dependent.eventSource(context)
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

  private DependentResource from(DependentResourceConfiguration config,
      KubernetesClient client) {
    if (config instanceof KubernetesDependentResourceConfiguration) {
      if (KubernetesDependentResource.class.isAssignableFrom(config.getDependentResourceClass())) {
        KubernetesDependentResourceInitializer dependentResourceInitializer =
            new KubernetesDependentResourceInitializer();
        return dependentResourceInitializer
            .initDependentResource((KubernetesDependentResourceConfiguration<?, ?>) config, client);
      } else {
        throw new IllegalArgumentException("A "
            + KubernetesDependentResourceConfiguration.class.getCanonicalName()
            + " must be associated to a " + KubernetesDependentResource.class.getCanonicalName());
      }
    } else {
      DependentResourceInitializer dependentResourceInitializer =
          new DependentResourceInitializer();
      return dependentResourceInitializer.initDependentResource(config, client);
    }
  }
}
