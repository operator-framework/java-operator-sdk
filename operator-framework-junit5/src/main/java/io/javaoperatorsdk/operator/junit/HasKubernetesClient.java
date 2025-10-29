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
package io.javaoperatorsdk.operator.junit;

import io.fabric8.kubernetes.client.KubernetesClient;

public interface HasKubernetesClient {
  /**
   * Returns the main Kubernetes client that is used to deploy the operator to the cluster.
   *
   * @return the main Kubernetes client
   */
  KubernetesClient getKubernetesClient();

  /**
   * Returns the Kubernetes client that is used to deploy infrastructure resources to the cluster
   * such as clusterroles, clusterrolebindings, etc. This client can be different from the main
   * client in case you need to test the operator with a different restrictions more closely
   * resembling the real restrictions it will have in production.
   *
   * @return the infrastructure Kubernetes client
   */
  KubernetesClient getInfrastructureKubernetesClient();
}
