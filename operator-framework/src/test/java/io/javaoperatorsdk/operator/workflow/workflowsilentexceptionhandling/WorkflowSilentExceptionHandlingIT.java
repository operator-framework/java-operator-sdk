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
package io.javaoperatorsdk.operator.workflow.workflowsilentexceptionhandling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Silent Workflow Exception Handling in Reconciler",
    description =
        """
        Demonstrates handling workflow exceptions silently within the reconciler rather than \
        propagating them. Tests verify that exceptions from dependent resources during both \
        reconciliation and cleanup are captured in the result object, allowing custom error \
        handling logic without failing the entire reconciliation.
        """)
public class WorkflowSilentExceptionHandlingIT {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(HandleWorkflowExceptionsInReconcilerReconciler.class)
          .build();

  @Test
  void handleExceptionsInReconciler() {
    extension.create(testResource());
    var reconciler =
        extension.getReconcilerOfType(HandleWorkflowExceptionsInReconcilerReconciler.class);

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isErrorsFoundInReconcilerResult()).isTrue();
            });

    extension.delete(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.isErrorsFoundInCleanupResult()).isTrue();
            });
  }

  HandleWorkflowExceptionsInReconcilerCustomResource testResource() {
    var res = new HandleWorkflowExceptionsInReconcilerCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName("test1").build());
    return res;
  }
}
