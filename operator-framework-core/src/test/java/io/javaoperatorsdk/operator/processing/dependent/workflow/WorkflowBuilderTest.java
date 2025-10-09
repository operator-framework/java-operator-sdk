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

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkflowBuilderTest {

  @Test
  void workflowIsCleanerIfAtLeastOneDRIsCleaner() {
    var dr = mock(DependentResource.class);
    when(dr.name()).thenReturn("dr");
    var deleter = mock(DependentResource.class);
    when(deleter.isDeletable()).thenReturn(true);
    when(deleter.name()).thenReturn("deleter");

    var workflow =
        new WorkflowBuilder<TestCustomResource>()
            .addDependentResource(deleter)
            .addDependentResource(dr)
            .build();

    assertThat(workflow.hasCleaner()).isTrue();
  }
}
