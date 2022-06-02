package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.*;
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
class ManagedWorkflowSupport<P extends HasMetadata> {

  public void checkForNameDuplication(List<DependentResourceSpec> dependentResourceSpecs) {
    if (dependentResourceSpecs.size() <= 1) {
      return;
    }
    var nameList =
        dependentResourceSpecs.stream().map(DependentResourceSpec::getName)
            .sorted().collect(Collectors.toList());
    for (int i = 0; i < nameList.size() - 1; i++) {
      if (nameList.get(i).equals(nameList.get(i + 1))) {
        throw new OperatorException("Duplicate dependent resource name:" + nameList.get(i));
      }
    }
  }

  @SuppressWarnings("unchecked")
  public Workflow<P> createWorkflow(List<DependentResourceSpec> dependentResourceSpecs,
      Map<String, DependentResource> dependentResourceByName) {
    var orderedResourceSpecs = orderAndDetectCycles(dependentResourceSpecs);
    var w = new WorkflowBuilder<P>();
    w.withThrowExceptionFurther(false);
    orderedResourceSpecs.forEach(spec -> {
      var drBuilder =
          w.addDependentResource(dependentResourceByName.get(spec.getName())).dependsOn(
              (Set<DependentResource>) spec.getDependsOn()
                  .stream().map(dependentResourceByName::get).collect(Collectors.toSet()));
      drBuilder.withDeletePostCondition(spec.getDeletePostCondition())
          .withReconcileCondition(spec.getReconcileCondition())
          .withReadyCondition(spec.getReadyCondition());
    });
    return w.build();
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
   * Throws also exception if there is a cycle in the dependencies.
   *
   * @param dependentResourceSpecs list of specs
   * @return top-bottom ordered resources that can be added safely to workflow
   *
   */
  public List<DependentResourceSpec> orderAndDetectCycles(
      List<DependentResourceSpec> dependentResourceSpecs) {

    List<DependentResourceSpec> res = new ArrayList<>(dependentResourceSpecs.size());
    Set<DependentResourceSpec> alreadySelected = new HashSet<>();
    Map<String, List<DependentResourceSpec>> dependOnIndex =
        createDependOnIndex(dependentResourceSpecs);
    Map<String, DependentResourceSpec> nameToDR = dependentResourceSpecs.stream()
        .collect(Collectors.toMap(DependentResourceSpec::getName, s -> s));
    Set<DependentResourceSpec> selectedLastIteration =
        getTopDependentResources(dependentResourceSpecs);

    while (!selectedLastIteration.isEmpty()) {
      res.addAll(selectedLastIteration);
      alreadySelected.addAll(selectedLastIteration);
      Set<DependentResourceSpec> newAdds = new HashSet<>();
      selectedLastIteration.forEach(dr -> {
        var dependsOn = dependOnIndex.get(dr.getName());
        if (dependsOn == null)
          dependsOn = Collections.emptyList();
        dependsOn.forEach(ndr -> {
          if (allDependsOnsAlreadySelected(ndr, alreadySelected, nameToDR, dr.getName())) {
            newAdds.add(ndr);
          }
        });
      });
      selectedLastIteration = newAdds;
    }

    if (res.size() != dependentResourceSpecs.size()) {
      // could provide improved message where the exact cycles are made visible
      throw new OperatorException(
          "Cycle(s) between dependent resources.");
    }
    return res;
  }

  private boolean allDependsOnsAlreadySelected(DependentResourceSpec dr,
      Set<DependentResourceSpec> alreadySelected,
      Map<String, DependentResourceSpec> nameToDR,
      String alreadyPresentName) {
    for (var name : dr.getDependsOn()) {
      if (name.equals(alreadyPresentName))
        continue;
      if (!alreadySelected.contains(nameToDR.get(name))) {
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

  private Map<String, List<DependentResourceSpec>> createDependOnIndex(
      List<DependentResourceSpec> dependentResourceSpecs) {
    Map<String, List<DependentResourceSpec>> dependsOnSpec = new HashMap<>();
    dependentResourceSpecs.forEach(dr -> dr.getDependsOn().forEach(name -> {
      dependsOnSpec.computeIfAbsent((String) name, n -> new ArrayList<>());
      dependsOnSpec.get(name).add(dr);
    }));
    return dependsOnSpec;
  }

}
