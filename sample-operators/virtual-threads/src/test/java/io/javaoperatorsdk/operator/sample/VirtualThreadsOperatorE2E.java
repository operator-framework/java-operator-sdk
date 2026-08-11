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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.ClusterDeployedOperatorExtension;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class VirtualThreadsOperatorE2E {

  static final Logger log = LoggerFactory.getLogger(VirtualThreadsOperatorE2E.class);

  static final KubernetesClient client = new KubernetesClientBuilder().build();

  public static final int RESOURCE_COUNT = 20;
  public static final String INITIAL_VALUE = "initial value";
  public static final String CHANGED_VALUE = "changed value";
  public static final Duration WAIT_TIMEOUT = Duration.ofSeconds(120);

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? LocallyRunOperatorExtension.builder()
              .withKubernetesClient(VirtualThreads.newKubernetesClient())
              .withConfigurationService(VirtualThreads::configureExecutors)
              .withReconciler(new VirtualThreadsReconciler())
              .build()
          : ClusterDeployedOperatorExtension.builder()
              .withOperatorDeployment(client.load(new FileInputStream("k8s/operator.yaml")).items())
              .build();

  public VirtualThreadsOperatorE2E() throws FileNotFoundException {}

  /**
   * All the resources block during their reconciliation, still all of them are reconciled in
   * parallel, without occupying a platform thread while waiting.
   */
  @Test
  void reconcilesAllResourcesOnVirtualThreads() {
    testResources(INITIAL_VALUE).forEach(r -> operator.create(r));

    awaitObservedValue(INITIAL_VALUE);

    testResources(CHANGED_VALUE).forEach(r -> operator.replace(r));

    awaitObservedValue(CHANGED_VALUE);

    testResources(CHANGED_VALUE).forEach(r -> operator.delete(r));

    await()
        .atMost(WAIT_TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(operator.resources(VirtualThreadsCustomResource.class).list().getItems())
                    .isEmpty());
  }

  void awaitObservedValue(String value) {
    await()
        .atMost(WAIT_TIMEOUT)
        .untilAsserted(
            () -> {
              for (int i = 0; i < RESOURCE_COUNT; i++) {
                var actual = operator.get(VirtualThreadsCustomResource.class, resourceName(i));
                assertThat(actual.getStatus()).isNotNull();
                assertThat(actual.getStatus().getObservedValue()).isEqualTo(value);
                assertThat(actual.getStatus().getReconciledOnVirtualThread()).isTrue();
              }
            });
  }

  List<VirtualThreadsCustomResource> testResources(String value) {
    return IntStream.range(0, RESOURCE_COUNT).mapToObj(i -> testResource(i, value)).toList();
  }

  VirtualThreadsCustomResource testResource(int index, String value) {
    var resource = new VirtualThreadsCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(resourceName(index))
            .withNamespace(operator.getNamespace())
            .build());
    resource.setSpec(new VirtualThreadsSpec());
    resource.getSpec().setValue(value);
    return resource;
  }

  String resourceName(int index) {
    return "test-" + index;
  }

  boolean isLocal() {
    var deployment = System.getProperty("test.deployment");
    boolean remote = deployment != null && deployment.equals("remote");
    log.info("Running the operator {}", remote ? "remote" : "locally");
    return !remote;
  }
}
