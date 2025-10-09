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

import java.util.Optional;

import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;

public interface ManagedWorkflowFactory<C extends ControllerConfiguration<?>> {

  @SuppressWarnings({"rawtypes", "unchecked"})
  ManagedWorkflowFactory DEFAULT =
      (configuration) -> {
        final Optional<WorkflowSpec> workflowSpec = configuration.getWorkflowSpec();
        if (workflowSpec.isEmpty()) {
          return (ManagedWorkflow) (client, configuration1) -> new DefaultWorkflow(null);
        }
        ManagedWorkflowSupport support = new ManagedWorkflowSupport();
        return support.createWorkflow(workflowSpec.orElseThrow());
      };

  @SuppressWarnings("rawtypes")
  ManagedWorkflow workflowFor(C configuration);
}
