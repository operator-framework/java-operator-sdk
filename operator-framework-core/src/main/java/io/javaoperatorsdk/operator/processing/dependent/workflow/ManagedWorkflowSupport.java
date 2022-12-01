package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

@SuppressWarnings({"rawtypes", "unchecked"})
class ManagedWorkflowSupport {

  private final static ManagedWorkflowSupport instance = new ManagedWorkflowSupport();

  static ManagedWorkflowSupport instance() {
    return instance;
  }

  private ManagedWorkflowSupport() {}

  public void checkForNameDuplication(List<DependentResourceSpec> dependentResourceSpecs) {
    if (dependentResourceSpecs == null) {
      return;
    }
    final var size = dependentResourceSpecs.size();
    if (size == 0) {
      return;
    }

    final var uniqueNames = new HashSet<>(size);
    final var duplicatedNames = new HashSet<>(size);
    dependentResourceSpecs.forEach(spec -> {
      final var name = spec.getName();
      if (!uniqueNames.add(name)) {
        duplicatedNames.add(name);
      }
    });
    if (!duplicatedNames.isEmpty()) {
      throw new OperatorException("Duplicated dependent resource name(s): " + duplicatedNames);
    }
  }

  public <P extends HasMetadata> Workflow<P> createWorkflow(
      List<DependentResourceSpec> dependentResourceSpecs) {
    var orderedResourceSpecs = orderAndDetectCycles(dependentResourceSpecs);
    final var alreadyCreated = new ArrayList<DependentResourceNode>(orderedResourceSpecs.size());
    final boolean[] cleanerHolder = {false};
    final var nodes = orderedResourceSpecs.stream()
        .map(spec -> createFrom(spec, alreadyCreated, cleanerHolder))
        .collect(Collectors.toSet());
    return new Workflow<>(nodes, cleanerHolder[0]);
  }

  private DependentResourceNode createFrom(DependentResourceSpec spec,
      List<DependentResourceNode> alreadyCreated, boolean[] cleanerHolder) {
    final var node = new SpecDependentResourceNode<>(spec);
    alreadyCreated.add(node);
    // if any previously checked dependent was a cleaner, no need to check further
    cleanerHolder[0] = cleanerHolder[0] || Workflow.isDeletable(spec.getDependentResourceClass());
    spec.getDependsOn().forEach(depend -> {
      final DependentResourceNode dependsOn = alreadyCreated.stream()
          .filter(drn -> depend.equals(drn.getName())).findFirst()
          .orElseThrow();
      node.addDependsOnRelation(dependsOn);
    });
    return node;
  }

  /**
   * @param dependentResourceSpecs list of specs
   * @return top-bottom ordered resources that can be added safely to workflow
   * @throws OperatorException if there is a cycle in the dependencies
   */
  public List<DependentResourceSpec> orderAndDetectCycles(
      List<DependentResourceSpec> dependentResourceSpecs) {

    final var drInfosByName = createDRInfos(dependentResourceSpecs);
    final var orderedSpecs = new ArrayList<DependentResourceSpec>(dependentResourceSpecs.size());
    final var alreadyVisited = new HashSet<String>();
    var toVisit = getTopDependentResources(dependentResourceSpecs);

    while (!toVisit.isEmpty()) {
      final var toVisitNext = new HashSet<DependentResourceSpec>();
      toVisit.forEach(dr -> {
        final var name = dr.getName();
        var drInfo = drInfosByName.get(name);
        if (drInfo != null) {
          drInfo.waitingForCompletion.forEach(spec -> {
            if (isReadyForVisit(spec, alreadyVisited, name)) {
              toVisitNext.add(spec);
            }
          });
          orderedSpecs.add(dr);
        }
        alreadyVisited.add(name);
      });

      toVisit = toVisitNext;
    }

    if (orderedSpecs.size() != dependentResourceSpecs.size()) {
      // could provide improved message where the exact cycles are made visible
      throw new OperatorException("Cycle(s) between dependent resources.");
    }
    return orderedSpecs;
  }

  private static class DRInfo {

    private final DependentResourceSpec spec;
    private final List<DependentResourceSpec> waitingForCompletion;

    private DRInfo(DependentResourceSpec spec) {
      this.spec = spec;
      this.waitingForCompletion = new LinkedList<>();
    }

    void add(DependentResourceSpec spec) {
      waitingForCompletion.add(spec);
    }

    String name() {
      return spec.getName();
    }
  }

  private boolean isReadyForVisit(DependentResourceSpec dr, Set<String> alreadyVisited,
      String alreadyPresentName) {
    for (var name : dr.getDependsOn()) {
      if (name.equals(alreadyPresentName)) {
        continue;
      }
      if (!alreadyVisited.contains(name)) {
        return false;
      }
    }
    return true;
  }

  private Set<DependentResourceSpec> getTopDependentResources(
      List<DependentResourceSpec> dependentResourceSpecs) {
    return dependentResourceSpecs.stream().filter(r -> r.getDependsOn().isEmpty())
        .collect(Collectors.toSet());
  }

  private Map<String, DRInfo> createDRInfos(List<DependentResourceSpec> dependentResourceSpecs) {
    // first create mappings
    final var infos = dependentResourceSpecs.stream()
        .map(DRInfo::new)
        .collect(Collectors.toMap(DRInfo::name, Function.identity()));

    // then populate the reverse depends on information
    dependentResourceSpecs.forEach(spec -> spec.getDependsOn().forEach(name -> {
      final var drInfo = infos.get(name);
      drInfo.add(spec);
    }));

    return infos;
  }

}
