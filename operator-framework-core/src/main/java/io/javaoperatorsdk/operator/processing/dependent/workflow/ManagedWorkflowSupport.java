/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ManagedWorkflowSupport {

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
    dependentResourceSpecs.forEach(
        spec -> {
          final var name = spec.getName();
          if (!uniqueNames.add(name)) {
            duplicatedNames.add(name);
          }
        });
    if (!duplicatedNames.isEmpty()) {
      throw new OperatorException("Duplicated dependent resource name(s): " + duplicatedNames);
    }
  }

  public <P extends HasMetadata> ManagedWorkflow<P> createWorkflow(WorkflowSpec workflowSpec) {
    return createAsDefault(workflowSpec.getDependentResourceSpecs());
  }

  <P extends HasMetadata> DefaultManagedWorkflow<P> createAsDefault(
      List<DependentResourceSpec> dependentResourceSpecs) {
    final boolean[] cleanerHolder = {false};
    var orderedResourceSpecs = orderAndDetectCycles(dependentResourceSpecs, cleanerHolder);
    return new DefaultManagedWorkflow<>(orderedResourceSpecs, cleanerHolder[0]);
  }

  /**
   * @param dependentResourceSpecs list of specs
   * @return top-bottom ordered resources that can be added safely to workflow
   * @throws OperatorException if there is a cycle in the dependencies
   */
  private List<DependentResourceSpec> orderAndDetectCycles(
      List<DependentResourceSpec> dependentResourceSpecs, boolean[] cleanerHolder) {

    final var drInfosByName = createDRInfos(dependentResourceSpecs);
    final var orderedSpecs = new ArrayList<DependentResourceSpec>(dependentResourceSpecs.size());
    final var alreadyVisited = new HashSet<String>();
    var toVisit = getTopDependentResources(dependentResourceSpecs);

    while (!toVisit.isEmpty()) {
      final var toVisitNext = new HashSet<DependentResourceSpec>();
      toVisit.forEach(
          dr -> {
            if (cleanerHolder != null) {
              cleanerHolder[0] =
                  cleanerHolder[0] || DefaultWorkflow.isDeletable(dr.getDependentResourceClass());
            }
            final var name = dr.getName();
            var drInfo = drInfosByName.get(name);
            if (drInfo != null) {
              drInfo.waitingForCompletion.forEach(
                  spec -> {
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

  /**
   * @param dependentResourceSpecs list of specs
   * @return top-bottom ordered resources that can be added safely to workflow
   * @throws OperatorException if there is a cycle in the dependencies
   */
  public List<DependentResourceSpec> orderAndDetectCycles(
      List<DependentResourceSpec> dependentResourceSpecs) {
    return orderAndDetectCycles(dependentResourceSpecs, null);
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

  private boolean isReadyForVisit(
      DependentResourceSpec dr, Set<String> alreadyVisited, String alreadyPresentName) {
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
    return dependentResourceSpecs.stream()
        .filter(r -> r.getDependsOn().isEmpty())
        .collect(Collectors.toSet());
  }

  private Map<String, DRInfo> createDRInfos(List<DependentResourceSpec> dependentResourceSpecs) {
    // first create mappings
    final var infos =
        dependentResourceSpecs.stream()
            .map(DRInfo::new)
            .collect(Collectors.toMap(DRInfo::name, Function.identity()));

    // then populate the reverse depends on information
    dependentResourceSpecs.forEach(
        spec ->
            spec.getDependsOn()
                .forEach(
                    name -> {
                      final var drInfo = infos.get(name);
                      drInfo.add(spec);
                    }));

    return infos;
  }
}
