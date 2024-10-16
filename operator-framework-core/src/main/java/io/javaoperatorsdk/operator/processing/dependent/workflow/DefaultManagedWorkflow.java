package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

@SuppressWarnings("rawtypes")
public class DefaultManagedWorkflow<P extends HasMetadata> implements ManagedWorkflow<P> {
  private final DefaultWorkflow<P> workflow;
  private boolean resolved;
  private final List<DependentResourceSpec> orderedSpecs; // todo: remove

  @SuppressWarnings("unchecked")
  protected DefaultManagedWorkflow(List<DependentResourceSpec> orderedSpecs, boolean hasCleaner) {
    final var alreadyResolved = new HashMap<String, DependentResourceNode>(orderedSpecs.size());
    for (DependentResourceSpec spec : orderedSpecs) {
      final var node = new UnresolvedDependentResourceNode(spec);
      alreadyResolved.put(spec.getName(), node);
      spec.getDependsOn()
          .forEach(depend -> node.addDependsOnRelation(alreadyResolved.get(depend)));
    }

    this.workflow = new DefaultWorkflow<>(alreadyResolved.values(), false, hasCleaner);
    this.orderedSpecs = orderedSpecs; // todo: remove
  }

  @Override
  @SuppressWarnings("unused")
  public List<DependentResourceSpec> getOrderedSpecs() {
    return orderedSpecs;
  }

  protected Set<String> getTopLevelResources() {
    return workflow.getTopLevelDependentResources().stream().map(DependentResourceNode::name)
        .collect(Collectors.toSet());
  }

  protected Set<String> getBottomLevelResources() {
    return workflow.getBottomLevelResource().stream().map(DependentResourceNode::name)
        .collect(Collectors.toSet());
  }

  List<String> nodeNames() {
    return workflow.getDependentResourceNodes().keySet().stream().toList();
  }

  @Override
  public boolean hasCleaner() {
    return workflow.hasCleaner();
  }

  @Override
  public boolean isEmpty() {
    return workflow.isEmpty();
  }

  @Override
  @SuppressWarnings("unchecked")
  public Workflow<P> resolve(KubernetesClient client,
      ControllerConfiguration<P> configuration) {
    if (!resolved) {
      workflow.getDependentResourceNodes().values()
          .parallelStream()
          .filter(UnresolvedDependentResourceNode.class::isInstance)
          .map(UnresolvedDependentResourceNode.class::cast)
          .forEach(node -> node.resolve(configuration));
      resolved = true;
    }
    return workflow;
  }
}
