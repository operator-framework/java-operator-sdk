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
package io.javaoperatorsdk.operator.sample;

import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;

import static java.time.temporal.ChronoUnit.SECONDS;

public class ControllerNamespaceDeletionOperator {

  private static final Logger log =
      LoggerFactory.getLogger(ControllerNamespaceDeletionOperator.class);

  public static void main(String[] args) {

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("Shutting down...");
                  boolean allResourcesDeleted = waitUntilResourcesDeleted();
                  log.info("All resources within timeout: {}", allResourcesDeleted);
                }));

    Operator operator = new Operator();
    operator.register(
        new ControllerNamespaceDeletionReconciler(),
        ControllerConfigurationOverrider::watchingOnlyCurrentNamespace);
    operator.start();
  }

  private static boolean waitUntilResourcesDeleted() {
    try (var client = new KubernetesClientBuilder().build()) {
      var startTime = LocalTime.now();
      while (startTime.until(LocalTime.now(), SECONDS) < 20) {
        var items =
            client
                .resources(ControllerNamespaceDeletionCustomResource.class)
                .inNamespace(client.getConfiguration().getNamespace())
                .list()
                .getItems();
        log.info("Custom resource in namespace: {}", items);
        if (items.isEmpty()) {
          return true;
        }
      }
      return false;
    }
  }
}
