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
package io.javaoperatorsdk.operator.workflow.workflowactivationcleanup;

import org.junit.jupiter.api.*;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Workflow Activation Cleanup",
    description =
        """
        Demonstrates how workflow cleanup is handled when activation conditions are involved. This \
        test verifies that resources are properly cleaned up on operator startup even when \
        marked for deletion, ensuring no orphaned resources remain after restarts.
        """)
public class WorkflowActivationCleanupIT {

  private final KubernetesClient client = new KubernetesClientBuilder().build();
  private Operator operator;

  private String testNamespace;

  @BeforeEach
  void beforeEach(TestInfo testInfo) {
    LocallyRunOperatorExtension.applyCrd(WorkflowActivationCleanupCustomResource.class, client);

    testInfo
        .getTestMethod()
        .ifPresent(method -> testNamespace = KubernetesResourceUtil.sanitizeName(method.getName()));
    client.namespaces().resource(testNamespace(testNamespace)).create();
    operator = new Operator(o -> o.withCloseClientOnStop(false));
    operator.register(
        new WorkflowActivationCleanupReconciler(), o -> o.settingNamespaces(testNamespace));
  }

  @AfterEach
  void stopOperator() {
    client.namespaces().withName(testNamespace).delete();
    await()
        .untilAsserted(
            () -> {
              var ns = client.namespaces().withName(testNamespace).get();
              assertThat(ns).isNull();
            });
    operator.stop();
  }

  @Test
  void testCleanupOnMarkedResourceOnOperatorStartup() {
    var resource = client.resource(testResourceWithFinalizer()).create();
    client.resource(resource).delete();
    operator.start();

    await()
        .untilAsserted(
            () -> {
              var res = client.resource(resource).get();
              assertThat(res).isNull();
            });
  }

  private WorkflowActivationCleanupCustomResource testResourceWithFinalizer() {
    var resource = new WorkflowActivationCleanupCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("test1")
            .withFinalizers(
                "workflowactivationcleanupcustomresources.sample.javaoperatorsdk/finalizer")
            .withNamespace(testNamespace)
            .build());
    resource.setSpec(new WorkflowActivationCleanupSpec());
    resource.getSpec().setValue("val1");
    return resource;
  }

  private Namespace testNamespace(String name) {
    return new NamespaceBuilder()
        .withMetadata(new ObjectMetaBuilder().withName(name).build())
        .build();
  }
}
