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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow.THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;

@SuppressWarnings({"rawtypes", "unchecked"})
public class WorkflowBuilder<P extends HasMetadata> {

  private final Map<String, DependentResourceNode<?, P>> dependentResourceNodes = new HashMap<>();
  private boolean throwExceptionAutomatically = THROW_EXCEPTION_AUTOMATICALLY_DEFAULT;
  private boolean isCleaner = false;

  public WorkflowNodeConfigurationBuilder addDependentResourceAndConfigure(
      DependentResource dependentResource) {
    final var currentNode = doAddDependentResource(dependentResource);
    return new WorkflowNodeConfigurationBuilder(currentNode);
  }

  public WorkflowBuilder<P> addDependentResource(DependentResource dependentResource) {
    doAddDependentResource(dependentResource);
    return this;
  }

  private DependentResourceNode doAddDependentResource(DependentResource dependentResource) {
    final var currentNode = new DependentResourceNode<>(dependentResource);
    isCleaner = isCleaner || dependentResource.isDeletable();
    final var actualName = dependentResource.name();
    dependentResourceNodes.put(actualName, currentNode);
    return currentNode;
  }

  DependentResourceNode getNodeByDependentResource(DependentResource<?, ?> dependentResource) {
    // first check by name
    final var node = dependentResourceNodes.get(dependentResource.name());
    if (node != null) {
      return node;
    } else {
      return dependentResourceNodes.values().stream()
          .filter(dr -> dr.getDependentResource() == dependentResource)
          .findFirst()
          .orElseThrow();
    }
  }

  public WorkflowBuilder<P> withThrowExceptionFurther(boolean throwExceptionFurther) {
    this.throwExceptionAutomatically = throwExceptionFurther;
    return this;
  }

  public Workflow<P> build() {
    return buildAsDefaultWorkflow();
  }

  DefaultWorkflow<P> buildAsDefaultWorkflow() {
    return new DefaultWorkflow(
        new HashSet<>(dependentResourceNodes.values()), throwExceptionAutomatically, isCleaner);
  }

  public class WorkflowNodeConfigurationBuilder {
    private final DependentResourceNode currentNode;

    private WorkflowNodeConfigurationBuilder(DependentResourceNode currentNode) {
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

    public WorkflowNodeConfigurationBuilder dependsOn(Set<DependentResource> dependentResources) {
      for (var dependentResource : dependentResources) {
        var dependsOn = getNodeByDependentResource(dependentResource);
        currentNode.addDependsOnRelation(dependsOn);
      }
      return this;
    }

    public WorkflowNodeConfigurationBuilder dependsOn(DependentResource... dependentResources) {
      if (dependentResources != null) {
        return dependsOn(new HashSet<>(Arrays.asList(dependentResources)));
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
