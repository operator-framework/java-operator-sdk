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
package io.javaoperatorsdk.operator.dependent.dependentreinitialization;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

class DependentReInitializationIT {

  /**
   * In case dependent resource is managed by CDI (like in Quarkus) can be handy that the instance
   * is reused in tests.
   */
  @Test
  void dependentCanDeReInitialized() {
    var client = new KubernetesClientBuilder().build();
    LocallyRunOperatorExtension.applyCrd(DependentReInitializationCustomResource.class, client);

    var dependent = new ConfigMapDependentResource();

    startEndStopOperator(client, dependent);
    startEndStopOperator(client, dependent);
  }

  private static void startEndStopOperator(
      KubernetesClient client, ConfigMapDependentResource dependent) {
    Operator o1 = new Operator(o -> o.withCloseClientOnStop(false).withKubernetesClient(client));
    o1.register(new DependentReInitializationReconciler(dependent));
    o1.start();
    o1.stop();
  }
}
