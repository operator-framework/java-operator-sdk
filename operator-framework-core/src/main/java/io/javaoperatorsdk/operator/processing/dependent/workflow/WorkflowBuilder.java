package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow.THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;

@SuppressWarnings({"rawtypes", "unchecked"})
public class WorkflowBuilder<P extends HasMetadata> {

  private final Map<String, WorkflowNodePrecursor> dependentResourceNodes =
      new HashMap<>();
  private boolean throwExceptionAutomatically = THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;

  public WorkflowNodeConfigurationBuilder addDependentResourceAndConfigure(
      DependentResource dependentResource) {
    final var currentNode = doAddDependentResource(dependentResource);
    return new WorkflowNodeConfigurationBuilder(currentNode);
  }

  public WorkflowBuilder<P> addDependentResource(DependentResource dependentResource) {
    doAddDependentResource(dependentResource);
    return this;
  }

  private DependentResourceNodePrecursor doAddDependentResource(
      DependentResource dependentResource) {
    final var currentNode =
        new DependentResourceNodePrecursor(new DependentResourceNode(dependentResource));
    final var actualName = dependentResource.name();
    dependentResourceNodes.put(actualName, currentNode);
    return currentNode;
  }

  public WorkflowBuilder<P> withThrowExceptionFurther(boolean throwExceptionFurther) {
    this.throwExceptionAutomatically = throwExceptionFurther;
    return this;
  }

  public Workflow<P> build() {
    return buildAsDefaultWorkflow();
  }

  DefaultWorkflow<P> buildAsDefaultWorkflow() {
    final boolean[] cleanerHolder = {false};
    final List<? extends DependentResourceNode<?, ?>> nodes =
        orderAndDetectCycles(dependentResourceNodes.values(), cleanerHolder);
    return new DefaultWorkflow(nodes, throwExceptionAutomatically, cleanerHolder[0]);
  }

  private static class DRInfo {

    private final WorkflowNodePrecursor spec;
    private final List<WorkflowNodePrecursor> waitingForCompletion;

    private DRInfo(WorkflowNodePrecursor spec) {
      this.spec = spec;
      this.waitingForCompletion = new LinkedList<>();
    }

    void add(WorkflowNodePrecursor spec) {
      waitingForCompletion.add(spec);
    }

    String name() {
      return spec.name();
    }
  }

  private boolean isReadyForVisit(WorkflowNodePrecursor dr, Set<String> alreadyVisited,
      String alreadyPresentName) {
    for (var name : dr.dependsOnAsNames()) {
      if (name.equals(alreadyPresentName)) {
        continue;
      }
      if (!alreadyVisited.contains(name)) {
        return false;
      }
    }
    return true;
  }

  private Set<WorkflowNodePrecursor> getTopDependentResources(
      Collection<WorkflowNodePrecursor> dependentResourceSpecs) {
    return dependentResourceSpecs.stream()
        .filter(r -> r.dependsOnAsNames().isEmpty())
        .collect(Collectors.toSet());
  }

  private Map<String, DRInfo> createDRInfos(
      Collection<WorkflowNodePrecursor> dependentResourceSpecs) {
    // first create mappings
    final var infos = dependentResourceSpecs.stream()
        .map(DRInfo::new)
        .collect(Collectors.toMap(DRInfo::name, Function.identity()));

    // then populate the reverse depends on information
    dependentResourceSpecs.forEach(spec -> spec.dependsOnAsNames().forEach(name -> {
      final var drInfo = infos.get(name);
      drInfo.add(spec);
    }));

    return infos;
  }

  private List<? extends DependentResourceNode<?, ?>> orderAndDetectCycles(
      Collection<WorkflowNodePrecursor> dependentResourceSpecs, boolean[] cleanerHolder) {

    final var drInfosByName = createDRInfos(dependentResourceSpecs);
    final var orderedSpecs =
        new LinkedHashMap<String, DependentResourceNode<?, ?>>(dependentResourceSpecs.size());
    final var alreadyVisited = new HashSet<String>();
    var toVisit = getTopDependentResources(dependentResourceSpecs);

    while (!toVisit.isEmpty()) {
      final var toVisitNext = new HashSet<WorkflowNodePrecursor>();
      toVisit.forEach(dr -> {
        if (cleanerHolder != null) {
          cleanerHolder[0] =
              cleanerHolder[0] || DefaultWorkflow.isDeletable(dr.getDependentResourceClass());
        }
        final var name = dr.name();
        var drInfo = drInfosByName.get(name);
        if (drInfo != null) {
          drInfo.waitingForCompletion.forEach(spec -> {
            if (isReadyForVisit(spec, alreadyVisited, name)) {
              toVisitNext.add(spec);
            }
          });
          final var node = convertToNodeAndResolveRelations(dr, orderedSpecs);
          orderedSpecs.put(name, node);
        }
        alreadyVisited.add(name);
      });

      toVisit = toVisitNext;
    }

    if (orderedSpecs.size() != dependentResourceSpecs.size()) {
      // could provide improved message where the exact cycles are made visible
      throw new OperatorException("Cycle(s) between dependent resources.");
    }
    return orderedSpecs.values().stream().toList();
  }

  private DependentResourceNode<?, ?> convertToNodeAndResolveRelations(
      WorkflowNodePrecursor<?, ?> node, LinkedHashMap<String, DependentResourceNode<?, ?>> known) {
    final var drn = new DependentResourceNode(node);
    node.dependsOnAsNames().forEach(name -> drn.addDependsOnRelation(known.get(name)));
    return drn;
  }

  public class WorkflowNodeConfigurationBuilder {
    private final DependentResourceNodePrecursor currentNode;

    private WorkflowNodeConfigurationBuilder(DependentResourceNodePrecursor currentNode) {
      this.currentNode = currentNode;
    }

    public WorkflowBuilder<P> addDependentResource(DependentResource<?, ?> dependentResource) {
      return WorkflowBuilder.this.addDependentResource(dependentResource);
    }

    public WorkflowNodeConfigurationBuilder addDependentResourceAndConfigure(
        DependentResource<?, ?> dependentResource) {
      final var currentNode = WorkflowBuilder.this.doAddDependentResource(dependentResource);
      return new WorkflowNodeConfigurationBuilder(currentNode);
    }

    public Workflow<P> build() {
      return WorkflowBuilder.this.build();
    }

    DefaultWorkflow<P> buildAsDefaultWorkflow() {
      return WorkflowBuilder.this.buildAsDefaultWorkflow();
    }

    public WorkflowBuilder<P> withThrowExceptionFurther(boolean throwExceptionFurther) {
      return WorkflowBuilder.this.withThrowExceptionFurther(throwExceptionFurther);
    }

    public WorkflowNodeConfigurationBuilder dependsOn(DependentResource... dependentResources) {
      if (dependentResources != null) {
        for (var dependentResource : dependentResources) {
          currentNode.addDependsOn(dependentResource.name());
        }
      }
      return this;
    }

    public WorkflowNodeConfigurationBuilder withReconcilePrecondition(
        Condition reconcilePrecondition) {
      currentNode.setReconcilePrecondition(reconcilePrecondition);
      return this;
    }

    public WorkflowNodeConfigurationBuilder withReadyPostcondition(Condition readyPostcondition) {
      currentNode.setReadyPostcondition(readyPostcondition);
      return this;
    }

    public WorkflowNodeConfigurationBuilder withDeletePostcondition(Condition deletePostcondition) {
      currentNode.setDeletePostcondition(deletePostcondition);
      return this;
    }

    public WorkflowNodeConfigurationBuilder withActivationCondition(Condition activationCondition) {
      currentNode.setActivationCondition(activationCondition);
      return this;
    }
  }
}
