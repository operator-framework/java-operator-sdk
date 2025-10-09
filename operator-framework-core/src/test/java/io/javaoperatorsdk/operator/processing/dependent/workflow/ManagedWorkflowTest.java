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

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.workflow.WorkflowSpec;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;

import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowTestUtils.createDRS;
import static io.javaoperatorsdk.operator.processing.dependent.workflow.ManagedWorkflowTestUtils.createDRSWithTraits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes"})
class ManagedWorkflowTest {

  public static final String NAME = "name";

  @Test
  void checksIfWorkflowEmpty() {
    assertThat(managedWorkflow().isEmpty()).isTrue();
    assertThat(managedWorkflow(createDRS(NAME)).isEmpty()).isFalse();
  }

  @Test
  void isNotCleanerIfNoDeleter() {
    assertThat(managedWorkflow(createDRS(NAME)).hasCleaner()).isFalse();
  }

  @Test
  void isNotCleanerIfGarbageCollected() {
    assertThat(managedWorkflow(createDRSWithTraits(NAME, GarbageCollected.class)).hasCleaner())
        .isFalse();
  }

  @Test
  void isCleanerShouldWork() {
    assertThat(
            managedWorkflow(
                    createDRSWithTraits(NAME, GarbageCollected.class),
                    createDRSWithTraits("foo", Deleter.class))
                .hasCleaner())
        .isTrue();

    assertThat(
            managedWorkflow(
                    createDRSWithTraits("foo", Deleter.class),
                    createDRSWithTraits(NAME, GarbageCollected.class))
                .hasCleaner())
        .isTrue();
  }

  @Test
  void isCleanerIfHasDeleter() {
    var spec = createDRSWithTraits(NAME, Deleter.class);
    assertThat(managedWorkflow(spec).hasCleaner()).isTrue();
  }

  @SuppressWarnings("unchecked")
  ManagedWorkflow managedWorkflow(DependentResourceSpec... specs) {
    final var configuration = mock(ControllerConfiguration.class);

    var ws =
        new WorkflowSpec() {
          @Override
          public List<DependentResourceSpec> getDependentResourceSpecs() {
            return List.of(specs);
          }

          @Override
          public boolean isExplicitInvocation() {
            return false;
          }

          @Override
          public boolean handleExceptionsInReconciler() {
            return false;
          }
        };
    when(configuration.getWorkflowSpec()).thenReturn(Optional.of(ws));

    return new BaseConfigurationService().getWorkflowFactory().workflowFor(configuration);
  }
}
