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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.KubernetesClientAware;
import io.javaoperatorsdk.operator.processing.dependent.workflow.builder.WorkflowBuilder;

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

  @SuppressWarnings("unchecked")
  public <P extends HasMetadata> Workflow<P> createWorkflow(
      List<DependentResourceSpec> dependentResourceSpecs,
      Map<String, DependentResource> dependentResourceByName) {
    var orderedResourceSpecs = orderAndDetectCycles(dependentResourceSpecs);
    var workflowBuilder = new WorkflowBuilder<P>().withThrowExceptionFurther(false);
    orderedResourceSpecs.forEach(spec -> {
      final var dependentResource = dependentResourceByName.get(spec.getName());
      final var dependsOn = (Set<DependentResource>) spec.getDependsOn()
          .stream().map(dependentResourceByName::get).collect(Collectors.toSet());
      workflowBuilder
          .addDependentResource(dependentResource)
          .dependsOn(dependsOn)
          .withDeletePostcondition(spec.getDeletePostCondition())
          .withReconcilePrecondition(spec.getReconcileCondition())
          .withReadyPostcondition(spec.getReadyCondition());
    });
    return workflowBuilder.build();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public DependentResource createAndConfigureFrom(DependentResourceSpec spec,
      KubernetesClient client) {
    final var dependentResource =
        ConfigurationServiceProvider.instance().dependentResourceFactory().createFrom(spec);

    if (dependentResource instanceof KubernetesClientAware) {
      ((KubernetesClientAware) dependentResource).setKubernetesClient(client);
    }

    if (dependentResource instanceof DependentResourceConfigurator) {
      final var configurator = (DependentResourceConfigurator) dependentResource;
      spec.getDependentResourceConfiguration().ifPresent(configurator::configureWith);
    }
    return dependentResource;
  }

  /**
   *
   * @param dependentResourceSpecs list of specs
   * @return top-bottom ordered resources that can be added safely to workflow
   * @throws OperatorException if there is a cycle in the dependencies
   *
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
      if (name.equals(alreadyPresentName))
        continue;
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
