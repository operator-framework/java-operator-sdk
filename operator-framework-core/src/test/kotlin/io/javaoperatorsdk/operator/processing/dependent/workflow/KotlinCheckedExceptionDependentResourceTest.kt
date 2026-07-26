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
package io.javaoperatorsdk.operator.processing.dependent.workflow

import io.javaoperatorsdk.operator.AggregatedOperatorException
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedWorkflowAndDependentResourceContext
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource
import java.util.concurrent.Executors
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Smoke test verifying that JOSDK works properly when used from Kotlin.
 *
 * Unlike Java, Kotlin does not distinguish between checked and unchecked exceptions, so a Kotlin
 * [DependentResource] can throw a plain [java.lang.Exception] from `reconcile` without declaring
 * it, even though the Java `DependentResource.reconcile` method does not declare `throws
 * Exception`. Before https://github.com/operator-framework/java-operator-sdk/pull/2965 such an
 * exception was not caught by [NodeExecutor], since it only handled [RuntimeException], so it
 * would not have been recorded as an error and retries would not have been triggered. This test
 * makes sure such an exception is properly caught and reported, see
 * https://github.com/operator-framework/java-operator-sdk/issues/2967.
 */
class KotlinCheckedExceptionDependentResourceTest {

  private class CheckedException(message: String) : Exception(message)

  private class ThrowingDependentResource :
      DependentResource<String, TestCustomResource> {
    override fun reconcile(
        primary: TestCustomResource,
        context: Context<TestCustomResource>
    ): ReconcileResult<String> {
      throw CheckedException("checked exception thrown from Kotlin")
    }

    override fun resourceType(): Class<String> = String::class.java
  }

  @Test
  fun checkedExceptionThrownFromKotlinDependentResourceTriggersRetry() {
    val dependentResource = ThrowingDependentResource()

    val workflow =
        WorkflowBuilder<TestCustomResource>()
            .addDependentResource(dependentResource)
            .withThrowExceptionFurther(false)
            .build()

    @Suppress("UNCHECKED_CAST")
    val context = mock(Context::class.java) as Context<TestCustomResource>
    val executorService = Executors.newSingleThreadExecutor()
    try {
      `when`(context.managedWorkflowAndDependentResourceContext())
          .thenReturn(mock(ManagedWorkflowAndDependentResourceContext::class.java))
      `when`(context.workflowExecutorService).thenReturn(executorService)
      @Suppress("UNCHECKED_CAST")
      `when`(context.eventSourceRetriever())
          .thenReturn(mock(EventSourceRetriever::class.java) as EventSourceRetriever<TestCustomResource>)

      val result = workflow.reconcile(TestCustomResource(), context)

      // the checked exception was caught by the workflow executor, not left uncaught, so it is
      // reported as an error for the dependent resource, which is what allows JOSDK to retry the
      // reconciliation.
      assertThat(result.erroredDependents).containsOnlyKeys(dependentResource)
      assertThat(result.erroredDependents[dependentResource])
          .isInstanceOf(CheckedException::class.java)
      assertThrows<AggregatedOperatorException> { result.throwAggregateExceptionIfErrorsPresent() }
    } finally {
      executorService.shutdownNow()
    }
}
