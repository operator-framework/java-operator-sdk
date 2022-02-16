package io.javaoperatorsdk.operator.processing.dependent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigurationProvider;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ContextInitializer;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

@SuppressWarnings({"rawtypes", "unchecked"})
@Ignore
public class DependentResourceManager<P extends HasMetadata>
    implements EventSourceInitializer<P>, Reconciler<P> {

  private static final Logger log = LoggerFactory.getLogger(DependentResourceManager.class);

  private final Reconciler<P> reconciler;
  private final ControllerConfiguration<P> controllerConfiguration;
  private List<DependentResource> dependents;

  public DependentResourceManager(Controller<P> controller) {
    this.reconciler = controller.getReconciler();
    this.controllerConfiguration = controller.getConfiguration();
  }

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<P> context) {
    final var dependentResources = controllerConfiguration.getDependentResources();
    final var sources = new ArrayList<EventSource>(dependentResources.size());
    dependents =
        dependentResources.stream()
            .map(
                drc -> {
                  final var dependentResource = from(drc, context.getClient());
                  dependentResource
                      .eventSource(context)
                      .ifPresent(es -> sources.add((EventSource) es));
                  return dependentResource;
                })
            .collect(Collectors.toList());
    return sources;
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

  private DependentResource from(DependentResourceSpec dependentResourceSpec,
      KubernetesClient client) {
    try {
      DependentResource dependentResource =
          (DependentResource) dependentResourceSpec.getDependentResourceClass()
              .getConstructor().newInstance();
      if (dependentResource instanceof KubernetesClientAware) {
        ((KubernetesClientAware) dependentResource).setKubernetesClient(client);
      }

      var configuration = dependentResourceSpec.getDependentResourceConfiguration();
      if (reconciler instanceof DependentResourceConfigurationProvider) {
        final var provider = (DependentResourceConfigurationProvider) reconciler;
        configuration = provider.configurationFor(dependentResourceSpec);
      }

      configuration.ifPresent(dependentResource::configureWith);
      return dependentResource;
    } catch (InstantiationException | NoSuchMethodException | IllegalAccessException
        | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }
}
