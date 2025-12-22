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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;

abstract class NodeExecutor<R, P extends HasMetadata> implements Runnable {

  private static final Logger log = LoggerFactory.getLogger(NodeExecutor.class);

  private final DependentResourceNode<R, P> dependentResourceNode;
  private final AbstractWorkflowExecutor<P> workflowExecutor;

  protected NodeExecutor(
      DependentResourceNode<R, P> dependentResourceNode,
      AbstractWorkflowExecutor<P> workflowExecutor) {
    this.dependentResourceNode = dependentResourceNode;
    this.workflowExecutor = workflowExecutor;
  }

  @Override
  public void run() {
    try {
      doRun(dependentResourceNode);

    } catch (Exception e) {
      // Exception is required because of Kotlin
      workflowExecutor.handleExceptionInExecutor(dependentResourceNode, e);
    } catch (Error e) {
      log.error("java.lang.Error during execution", e);
      throw e;
    } finally {
      workflowExecutor.handleNodeExecutionFinish(dependentResourceNode);
    }
  }

  protected abstract void doRun(DependentResourceNode<R, P> dependentResourceNode);
}
