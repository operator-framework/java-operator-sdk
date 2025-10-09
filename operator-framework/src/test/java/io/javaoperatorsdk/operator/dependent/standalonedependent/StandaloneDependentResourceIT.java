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
package io.javaoperatorsdk.operator.dependent.standalonedependent;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.api.config.*;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Standalone Dependent Resources",
    description =
        """
        Demonstrates how to use standalone dependent resources that are managed independently \
        without explicit workflow configuration. This test shows how dependent resources \
        can be created and managed programmatically, with the dependent resource handling \
        CRUD operations on a Kubernetes Deployment. The test verifies both creation and \
        update scenarios, including cache updates when the dependent resource state \
        changes.
        """)
public class StandaloneDependentResourceIT {

  public static final String DEPENDENT_TEST_NAME = "dependent-test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new StandaloneDependentTestReconciler())
          .build();

  @Test
  void dependentResourceManagesDeployment() {
    StandaloneDependentTestCustomResource customResource =
        new StandaloneDependentTestCustomResource();
    customResource.setSpec(new StandaloneDependentTestCustomResourceSpec());
    customResource.setMetadata(new ObjectMeta());
    customResource.getMetadata().setName(DEPENDENT_TEST_NAME);

    operator.create(customResource);

    awaitForDeploymentReadyReplicas(1);
    assertThat(
            ((StandaloneDependentTestReconciler) operator.getFirstReconciler()).isErrorOccurred())
        .isFalse();
  }

  @Test
  void executeUpdateForTestingCacheUpdateForGetResource() {
    StandaloneDependentTestCustomResource customResource =
        new StandaloneDependentTestCustomResource();
    customResource.setSpec(new StandaloneDependentTestCustomResourceSpec());
    customResource.setMetadata(new ObjectMeta());
    customResource.getMetadata().setName(DEPENDENT_TEST_NAME);
    var createdCR = operator.create(customResource);

    awaitForDeploymentReadyReplicas(1);

    var clonedCr = cloner().clone(createdCR);
    clonedCr.getSpec().setReplicaCount(2);
    operator.replace(clonedCr);

    awaitForDeploymentReadyReplicas(2);
    assertThat(
            ((StandaloneDependentTestReconciler) operator.getFirstReconciler()).isErrorOccurred())
        .isFalse();
  }

  void awaitForDeploymentReadyReplicas(int expectedReplicaCount) {
    await()
        .pollInterval(Duration.ofMillis(300))
        .atMost(Duration.ofSeconds(50))
        .until(
            () -> {
              var deployment =
                  operator
                      .getKubernetesClient()
                      .resources(Deployment.class)
                      .inNamespace(operator.getNamespace())
                      .withName(DEPENDENT_TEST_NAME)
                      .get();
              return deployment != null
                  && deployment.getStatus() != null
                  && deployment.getStatus().getReadyReplicas() != null
                  && deployment.getStatus().getReadyReplicas() == expectedReplicaCount;
            });
  }

  Cloner cloner() {
    return new ConfigurationService() {
      @Override
      public <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(
          Reconciler<R> reconciler) {
        return null;
      }

      @Override
      public Set<String> getKnownReconcilerNames() {
        return null;
      }

      @Override
      public Version getVersion() {
        return null;
      }
    }.getResourceCloner();
  }
}
