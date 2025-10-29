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
package io.javaoperatorsdk.operator.baseapi.leaderelectionchangenamespace;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class LeaderElectionChangeNamespaceIT {

  public static final String LEASE_NAME = "nschangelease";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(
              o -> o.withLeaderElectionConfiguration(new LeaderElectionConfiguration(LEASE_NAME)))
          .withReconciler(new LeaderElectionChangeNamespaceReconciler())
          .build();

  private static KubernetesClient client = new KubernetesClientBuilder().build();

  @BeforeAll
  static void createLeaseManually() {
    client.resource(lease()).create();
  }

  @AfterAll
  static void deleteLeaseManually() {
    client.resource(lease()).delete();
  }

  @Test
  @DisplayName("If operator is not a leader, namespace change should not start processor")
  void noReconcileOnChangeNamespace() {
    extension.create(testResource());

    var reconciler = extension.getReconcilerOfType(LeaderElectionChangeNamespaceReconciler.class);
    await()
        .pollDelay(Duration.ofSeconds(1))
        .timeout(Duration.ofSeconds(3))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isEqualTo(0);
            });

    extension
        .getRegisteredControllerForReconcile(LeaderElectionChangeNamespaceReconciler.class)
        .changeNamespaces("default", extension.getNamespace());

    await()
        .pollDelay(Duration.ofSeconds(1))
        .timeout(Duration.ofSeconds(3))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isEqualTo(0);
            });
  }

  LeaderElectionChangeNamespaceCustomResource testResource() {
    var resource = new LeaderElectionChangeNamespaceCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName("test1").build());
    return resource;
  }

  static Lease lease() {
    var lease = new Lease();
    lease.setMetadata(
        new ObjectMetaBuilder().withName(LEASE_NAME).withNamespace("default").build());
    var time = ZonedDateTime.now();
    lease.setSpec(
        new LeaseSpecBuilder()
            .withAcquireTime(ZonedDateTime.now())
            .withRenewTime(time)
            .withAcquireTime(time)
            .withHolderIdentity("non-operator-identity")
            .withLeaseTransitions(0)
            .withLeaseDurationSeconds(30)
            .build());

    return lease;
  }
}
