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
package io.javaoperatorsdk.operator.sample.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.ClusterDeployedOperatorExtension;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

class MetricsHandlingE2E {
  static final Logger log = LoggerFactory.getLogger(MetricsHandlingE2E.class);

  boolean isLocal() {
    String deployment = System.getProperty("test.deployment");
    boolean remote = (deployment != null && deployment.equals("remote"));
    log.info("Running the operator {} ", remote ? "remotely" : "locally");
    return !remote;
  }

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? LocallyRunOperatorExtension.builder()
              .withReconciler(new MetricsHandlingReconciler1())
              .withReconciler(new MetricsHandlingReconciler2())
              // SchemaDependentResource annotation
              //                    .withInfrastructure(infrastructure)
              //                    .withPortForward(MY_SQL_NS, "app", "mysql", 3306,
              // SchemaDependentResource.LOCAL_PORT)
              .build()
          : ClusterDeployedOperatorExtension.builder()
              //                    .withOperatorDeployment(client.load(new
              // FileInputStream("k8s/operator.yaml")).items())
              //                    .withInfrastructure(infrastructure)
              .build();

  @Test
  void testPropagatedMetrics() {}
}
