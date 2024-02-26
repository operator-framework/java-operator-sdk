package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceReferencer;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_VALUE_SET;
import static io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow.THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;

@SuppressWarnings("rawtypes")
public class DefaultManagedWorkflow<P extends HasMetadata> implements ManagedWorkflow<P> {

  private final Set<String> topLevelResources;
  private final Set<String> bottomLevelResources;
  private final List<DependentResourceSpec> orderedSpecs;
  private final boolean hasCleaner;

  protected DefaultManagedWorkflow(List<DependentResourceSpec> orderedSpecs, boolean hasCleaner) {
    this.hasCleaner = hasCleaner;
    topLevelResources = new HashSet<>(orderedSpecs.size());
    bottomLevelResources = orderedSpecs.stream()
        .map(DependentResourceSpec::getName)
        .collect(Collectors.toSet());
    this.orderedSpecs = orderedSpecs;
    for (DependentResourceSpec<?, ?> spec : orderedSpecs) {
      // add cycle detection?
      if (spec.getDependsOn().isEmpty()) {
        topLevelResources.add(spec.getName());
      } else {
        for (String dependsOn : spec.getDependsOn()) {
          bottomLevelResources.remove(dependsOn);
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unused")
  public List<DependentResourceSpec> getOrderedSpecs() {
    return orderedSpecs;
  }

  protected Set<String> getTopLevelResources() {
    return topLevelResources;
  }

  protected Set<String> getBottomLevelResources() {
    return bottomLevelResources;
  }

  List<String> nodeNames() {
    return orderedSpecs.stream().map(DependentResourceSpec::getName).collect(Collectors.toList());
  }

  @Override
  public boolean hasCleaner() {
    return hasCleaner;
  }

  @Override
  public boolean isEmpty() {
    return orderedSpecs.isEmpty();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Workflow<P> resolve(KubernetesClient client,
      ControllerConfiguration<P> configuration) {
    final var alreadyResolved = new HashMap<String, DependentResourceNode>(orderedSpecs.size());
    for (DependentResourceSpec spec : orderedSpecs) {
      final var node = new DependentResourceNode(
          spec.getReconcileCondition(),
          spec.getDeletePostCondition(),
          spec.getReadyCondition(),
          spec.getActivationCondition(),
          resolve(spec, client, configuration));
      alreadyResolved.put(node.getDependentResource().getName(), node);
      spec.getDependsOn()
          .forEach(depend -> node.addDependsOnRelation(alreadyResolved.get(depend)));
    }

    final var bottom =
        bottomLevelResources.stream().map(alreadyResolved::get).collect(Collectors.toSet());
    final var top =
        topLevelResources.stream().map(alreadyResolved::get).collect(Collectors.toSet());
    return new DefaultWorkflow<>(alreadyResolved, bottom, top,
        THROW_EXCEPTION_AUTOMATICALLY_DEFAULT, hasCleaner);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private <R> DependentResource<R, P> resolve(DependentResourceSpec<R, P> spec,
      KubernetesClient client,
      ControllerConfiguration<P> configuration) {
    final DependentResource<R, P> dependentResource =
        configuration.getConfigurationService().dependentResourceFactory()
            .createFrom(spec, configuration);

    if (spec.getName() != null && !spec.getName().equals(NO_VALUE_SET)) {
      dependentResource.setName(spec.getName());
    }

    if (dependentResource instanceof KubernetesClientAware) {
      ((KubernetesClientAware) dependentResource).setKubernetesClient(client);
    }

    spec.getUseEventSourceWithName()
        .ifPresent(esName -> {
          if (dependentResource instanceof EventSourceReferencer) {
            ((EventSourceReferencer) dependentResource).useEventSourceWithName(esName);
          } else {
            throw new IllegalStateException(
                "DependentResource " + spec + " wants to use EventSource named " + esName
                    + " but doesn't implement support for this feature by implementing "
                    + EventSourceReferencer.class.getSimpleName());
          }
        });

    return dependentResource;
  }
}
