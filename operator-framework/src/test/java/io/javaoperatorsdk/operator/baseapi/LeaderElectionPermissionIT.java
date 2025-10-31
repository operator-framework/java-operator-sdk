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
package io.javaoperatorsdk.operator.baseapi;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static io.javaoperatorsdk.operator.LeaderElectionManager.NO_PERMISSION_TO_LEASE_RESOURCE_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LeaderElectionPermissionIT {

  KubernetesClient adminClient = new KubernetesClientBuilder().build();

  @Test
  void operatorStopsIfNoLeaderElectionPermission() {
    applyRole();
    applyRoleBinding();

    var client =
        new KubernetesClientBuilder()
            .withConfig(
                new ConfigBuilder().withImpersonateUsername("leader-elector-stop-noaccess").build())
            .build();

    var operator =
        new Operator(
            o -> {
              o.withKubernetesClient(client);
              o.withLeaderElectionConfiguration(
                  new LeaderElectionConfiguration("lease1", "default"));
              o.withStopOnInformerErrorDuringStartup(false);
            });
    operator.register(new TestReconciler(), o -> o.settingNamespace("default"));

    OperatorException exception = assertThrows(OperatorException.class, operator::start);

    assertThat(exception.getCause().getMessage()).contains(NO_PERMISSION_TO_LEASE_RESOURCE_MESSAGE);
  }

  @ControllerConfiguration
  public static class TestReconciler implements Reconciler<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      throw new IllegalStateException("Should not get here");
    }
  }

  private void applyRoleBinding() {
    var clusterRoleBinding =
        ReconcilerUtilsInternal.loadYaml(
            RoleBinding.class, this.getClass(), "leader-elector-stop-noaccess-role-binding.yaml");
    adminClient.resource(clusterRoleBinding).createOrReplace();
  }

  private void applyRole() {
    var role =
        ReconcilerUtilsInternal.loadYaml(
            Role.class, this.getClass(), "leader-elector-stop-role-noaccess.yaml");
    adminClient.resource(role).createOrReplace();
  }
}
