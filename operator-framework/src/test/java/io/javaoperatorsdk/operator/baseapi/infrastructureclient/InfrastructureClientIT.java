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
package io.javaoperatorsdk.operator.baseapi.infrastructureclient;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class InfrastructureClientIT {

  private static final String RBAC_TEST_ROLE = "rbac-test-role.yaml";
  private static final String RBAC_TEST_ROLE_BINDING = "rbac-test-role-binding.yaml";
  private static final String RBAC_TEST_USER = "rbac-test-user";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new InfrastructureClientTestReconciler())
          .withKubernetesClient(
              new KubernetesClientBuilder()
                  .withConfig(new ConfigBuilder().withImpersonateUsername(RBAC_TEST_USER).build())
                  .build())
          .withInfrastructureKubernetesClient(
              new KubernetesClientBuilder().build()) // no limitations
          .build();

  /**
   * We need to apply the cluster role also before the CRD deployment so the rbac-test-user is
   * permitted to deploy it
   */
  public InfrastructureClientIT() {
    applyClusterRole(RBAC_TEST_ROLE);
    applyClusterRoleBinding(RBAC_TEST_ROLE_BINDING);
  }

  @BeforeEach
  void setup() {
    applyClusterRole(RBAC_TEST_ROLE);
    applyClusterRoleBinding(RBAC_TEST_ROLE_BINDING);
  }

  @AfterEach
  void cleanup() {
    removeClusterRoleBinding(RBAC_TEST_ROLE_BINDING);
    removeClusterRole(RBAC_TEST_ROLE);
  }

  @Test
  void canCreateInfrastructure() {
    var resource = new InfrastructureClientTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder().withName("infrastructure-client-resource").build());
    operator.create(resource);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              InfrastructureClientTestCustomResource r =
                  operator.get(
                      InfrastructureClientTestCustomResource.class,
                      "infrastructure-client-resource");
              assertThat(r).isNotNull();
            });

    assertThat(
            operator
                .getReconcilerOfType(InfrastructureClientTestReconciler.class)
                .getNumberOfExecutions())
        .isEqualTo(1);
  }

  @Test
  void shouldNotAccessNotPermittedResources() {
    assertThatThrownBy(
            () ->
                operator
                    .getKubernetesClient()
                    .apiextensions()
                    .v1()
                    .customResourceDefinitions()
                    .list())
        .isInstanceOf(KubernetesClientException.class)
        .hasMessageContaining(
            "User \"%s\" cannot list resource \"customresourcedefinitions\""
                .formatted(RBAC_TEST_USER));

    // but we should be able to access all resources with the infrastructure client
    var deploymentList =
        operator
            .getInfrastructureKubernetesClient()
            .apiextensions()
            .v1()
            .customResourceDefinitions()
            .list();
    assertThat(deploymentList).isNotNull();
  }

  private void applyClusterRoleBinding(String filename) {
    var clusterRoleBinding =
        ReconcilerUtilsInternal.loadYaml(ClusterRoleBinding.class, this.getClass(), filename);
    operator.getInfrastructureKubernetesClient().resource(clusterRoleBinding).serverSideApply();
  }

  private void applyClusterRole(String filename) {
    var clusterRole =
        ReconcilerUtilsInternal.loadYaml(ClusterRole.class, this.getClass(), filename);
    operator.getInfrastructureKubernetesClient().resource(clusterRole).serverSideApply();
  }

  private void removeClusterRoleBinding(String filename) {
    var clusterRoleBinding =
        ReconcilerUtilsInternal.loadYaml(ClusterRoleBinding.class, this.getClass(), filename);
    operator.getInfrastructureKubernetesClient().resource(clusterRoleBinding).delete();
  }

  private void removeClusterRole(String filename) {
    var clusterRole =
        ReconcilerUtilsInternal.loadYaml(ClusterRole.class, this.getClass(), filename);
    operator.getInfrastructureKubernetesClient().resource(clusterRole).delete();
  }
}
