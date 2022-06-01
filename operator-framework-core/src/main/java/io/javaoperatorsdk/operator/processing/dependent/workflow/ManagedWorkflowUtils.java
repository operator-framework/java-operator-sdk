package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.*;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

@SuppressWarnings({"rawtypes", "unchecked"})
class ManagedWorkflowUtils {

  public static void checkForNameDuplication(List<DependentResourceSpec> dependentResourceSpecs) {
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

  /**
   * Throws also exception if there is a cycle in the dependencies.
   *
   * @param dependentResourceSpecs list of specs
   * @return top-bottom ordered resources that can be added safely to workflow
   *
   */
  public static List<DependentResourceSpec> orderAndDetectCycles(
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



  private static boolean allDependsOnsAlreadySelected(DependentResourceSpec dr,
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

  private static Set<DependentResourceSpec> getTopDependentResources(
      List<DependentResourceSpec> dependentResourceSpecs) {
    return dependentResourceSpecs.stream().filter(r -> r.getDependsOn().isEmpty())
        .collect(Collectors.toSet());
  }

  private static Map<String, List<DependentResourceSpec>> createDependOnIndex(
      List<DependentResourceSpec> dependentResourceSpecs) {
    Map<String, List<DependentResourceSpec>> dependsOnSpec = new HashMap<>();
    dependentResourceSpecs.forEach(dr -> dr.getDependsOn().forEach(name -> {
      dependsOnSpec.computeIfAbsent((String) name, n -> new ArrayList<>());
      dependsOnSpec.get(name).add(dr);
    }));
    return dependsOnSpec;
  }

}
