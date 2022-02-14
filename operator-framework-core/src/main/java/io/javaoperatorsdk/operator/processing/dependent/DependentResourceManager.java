package io.javaoperatorsdk.operator.processing.dependent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
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
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ManagedDependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.kubernetes.KubernetesDependentResourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@SuppressWarnings({"rawtypes", "unchecked"})
@Ignore
public class DependentResourceManager<P extends HasMetadata>
    implements EventSourceInitializer<P>, EventSourceContextInjector, Reconciler<P> {
  private final Reconciler<P> reconciler;
  private final ControllerConfiguration<P> controllerConfiguration;
  private List<DependentResource> dependents;
  private Map<Class<? extends DependentResourceInitializer>, DependentResourceInitializer> initializers =
      new HashMap();

  public DependentResourceManager(Controller<P> controller) {
    this.reconciler = controller.getReconciler();
    this.controllerConfiguration = controller.getConfiguration();
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<P> context) {
    final var dependentConfigurations = controllerConfiguration.getDependentResources();
    final var sources = new ArrayList<EventSource>(dependentConfigurations.size());
    dependents =
        dependentConfigurations.stream()
            .map(
                drc -> {
                  final var dependentResource =
                      from(drc, context.getClient());
                  dependentResource
                      .eventSource(context)
                      .ifPresent(es -> sources.add((EventSource) es));
                  return dependentResource;
                })
            .collect(Collectors.toList());
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

  private DependentResourceInitializer getOrInitInitializerForClass(
      Class<? extends DependentResource> dependentResourceClass) {
    try {
      Class<? extends DependentResourceInitializer> initializerClass;

      var managedDependentResource =
          dependentResourceClass.getAnnotation(ManagedDependentResource.class);

      if (managedDependentResource == null) {
        if (KubernetesDependentResource.class.isAssignableFrom(dependentResourceClass)) {
          // KubernetesDependentResourceInitializer is specially covered so annotation is not
          // repeated
          initializerClass = KubernetesDependentResourceInitializer.class;
        } else {
          throw new OperatorException(
              "No initializer found for class: "
                  + dependentResourceClass.getName()
                  + ". "
                  + "Use  @ManagedDependentResource annotation to specify it.");
        }
      } else {
        initializerClass = managedDependentResource.initializer();
      }

      var initializer = initializers.get(dependentResourceClass);
      if (initializer == null) {

        initializer = initializerClass.getConstructor().newInstance();

        initializers.put(initializerClass, initializer);
      }
      return initializer;
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private DependentResource from(
      Class<? extends DependentResource> dependentResourceClass,
      KubernetesClient client) {
    var initializer = getOrInitInitializerForClass(dependentResourceClass);
    return initializer.initialize(dependentResourceClass, controllerConfiguration, client);
  }
}
