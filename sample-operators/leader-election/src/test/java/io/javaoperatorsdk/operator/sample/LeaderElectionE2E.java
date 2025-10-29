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
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.sample.v1.LeaderElection;

import static io.javaoperatorsdk.operator.junit.AbstractOperatorExtension.CRD_READY_WAIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LeaderElectionE2E {

  private static final Logger log = LoggerFactory.getLogger(LeaderElectionE2E.class);
  public static final int POD_STARTUP_TIMEOUT = 60;

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final int MINIMAL_SECONDS_FOR_RENEWAL = 3;
  public static final int MAX_WAIT_SECONDS = 30;

  private static final String OPERATOR_1_POD_NAME = "leader-election-operator-1";
  private static final String OPERATOR_2_POD_NAME = "leader-election-operator-2";
  public static final int MINIMAL_EXPECTED_RECONCILIATION = 3;

  private String namespace;
  private KubernetesClient client;

  @ParameterizedTest
  @ValueSource(strings = {"namespace-inferred-", ""})
  // not for local mode by design
  @EnabledIfSystemProperty(named = "test.deployment", matches = "remote")
  void otherInstancesTakesOverWhenSteppingDown(String yamlFilePrefix) {
    log.info("Applying custom resource");
    applyCustomResource();
    log.info("Deploying operator instances");
    deployOperatorsInOrder(yamlFilePrefix);

    log.info("Awaiting custom resource reconciliations");
    await()
        .pollDelay(Duration.ofSeconds(MINIMAL_SECONDS_FOR_RENEWAL))
        .atMost(Duration.ofSeconds(MAX_WAIT_SECONDS))
        .untilAsserted(
            () -> {
              var actualStatus =
                  client
                      .resources(LeaderElection.class)
                      .inNamespace(namespace)
                      .withName(TEST_RESOURCE_NAME)
                      .get()
                      .getStatus();

              assertThat(actualStatus).isNotNull();
              assertThat(actualStatus.getReconciledBy())
                  .hasSizeGreaterThan(MINIMAL_EXPECTED_RECONCILIATION);
            });

    client.pods().inNamespace(namespace).withName(OPERATOR_1_POD_NAME).delete();

    var actualListSize =
        client
            .resources(LeaderElection.class)
            .inNamespace(namespace)
            .withName(TEST_RESOURCE_NAME)
            .get()
            .getStatus()
            .getReconciledBy()
            .size();

    await()
        .pollDelay(Duration.ofSeconds(MINIMAL_SECONDS_FOR_RENEWAL))
        .atMost(Duration.ofSeconds(240))
        .untilAsserted(
            () -> {
              var actualStatus =
                  client
                      .resources(LeaderElection.class)
                      .inNamespace(namespace)
                      .withName(TEST_RESOURCE_NAME)
                      .get()
                      .getStatus();

              assertThat(actualStatus).isNotNull();
              assertThat(actualStatus.getReconciledBy())
                  .hasSizeGreaterThan(actualListSize + MINIMAL_EXPECTED_RECONCILIATION);
            });

    assertReconciliations(
        client
            .resources(LeaderElection.class)
            .inNamespace(namespace)
            .withName(TEST_RESOURCE_NAME)
            .get()
            .getStatus()
            .getReconciledBy());
  }

  private void assertReconciliations(List<String> reconciledBy) {
    log.info("Reconciled by content: {}", reconciledBy);
    OptionalInt firstO2StatusIndex =
        IntStream.range(0, reconciledBy.size())
            .filter(i -> reconciledBy.get(i).equals(OPERATOR_2_POD_NAME))
            .findFirst();
    assertThat(firstO2StatusIndex).isPresent();
    assertThat(reconciledBy.subList(0, firstO2StatusIndex.getAsInt() - 1))
        .allMatch(s -> s.equals(OPERATOR_1_POD_NAME));
    assertThat(reconciledBy.subList(firstO2StatusIndex.getAsInt(), reconciledBy.size()))
        .allMatch(s -> s.equals(OPERATOR_2_POD_NAME));
  }

  private void applyCustomResource() {
    var res = new LeaderElection();
    res.setMetadata(
        new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).withNamespace(namespace).build());
    client.resource(res).create();
  }

  @BeforeEach
  void setup() {
    namespace = "leader-election-it-" + UUID.randomUUID();
    client =
        new KubernetesClientBuilder()
            .withConfig(new ConfigBuilder().withNamespace(namespace).build())
            .build();
    applyCRD();
    client
        .namespaces()
        .resource(
            new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build())
        .create();
  }

  @AfterEach
  void tearDown() {
    client
        .namespaces()
        .resource(
            new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build())
        .delete();
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(() -> assertThat(client.namespaces().withName(namespace).get()).isNull());
  }

  private void deployOperatorsInOrder(String yamlFilePrefix) {
    log.info("Installing 1st instance");
    applyResources("k8s/" + yamlFilePrefix + "operator.yaml");
    await()
        .atMost(Duration.ofSeconds(POD_STARTUP_TIMEOUT))
        .untilAsserted(
            () -> {
              var pod = client.pods().inNamespace(namespace).withName(OPERATOR_1_POD_NAME).get();
              assertThat(pod.getStatus().getContainerStatuses()).isNotEmpty();
              assertThat(pod.getStatus().getContainerStatuses().get(0).getReady()).isTrue();
            });

    log.info("Installing 2nd instance");
    applyResources("k8s/" + yamlFilePrefix + "operator-instance-2.yaml");
    await()
        .atMost(Duration.ofSeconds(POD_STARTUP_TIMEOUT))
        .untilAsserted(
            () -> {
              var pod = client.pods().inNamespace(namespace).withName(OPERATOR_2_POD_NAME).get();
              assertThat(pod.getStatus().getContainerStatuses()).isNotEmpty();
              assertThat(pod.getStatus().getContainerStatuses().get(0).getReady()).isTrue();
            });
  }

  void applyCRD() {
    String path = "./src/main/resources/kubernetes/leaderelections.sample.javaoperatorsdk-v1.yml";
    try (InputStream is = new FileInputStream(path)) {
      final var crd = client.load(is);
      crd.createOrReplace();
      Thread.sleep(CRD_READY_WAIT);
      log.debug("Applied CRD with name: {}", crd.get().get(0).getMetadata().getName());
    } catch (InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  void applyResources(String path) {
    try {
      List<HasMetadata> resources = client.load(new FileInputStream(path)).items();
      resources.forEach(
          hm -> {
            hm.getMetadata().setNamespace(namespace);
            if (hm.getKind().toLowerCase(Locale.ROOT).equals("clusterrolebinding")) {
              var crb = (ClusterRoleBinding) hm;
              for (var subject : crb.getSubjects()) {
                subject.setNamespace(namespace);
              }
            }
          });
      client.resourceList(resources).inNamespace(namespace).createOrReplace();

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
