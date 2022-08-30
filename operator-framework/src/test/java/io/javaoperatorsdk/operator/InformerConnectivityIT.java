package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.UUID;

import io.javaoperatorsdk.operator.junit.UnitTestUtils;
import io.javaoperatorsdk.operator.sample.informerconnectivity.InformerConnectivityTestCustomResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.sample.informerconnectivity.InformerConnectivityTestCustomReconciler;
import io.javaoperatorsdk.operator.sample.informerconnectivity.SimpleConnectivityTestCustomReconciler;


import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InformerConnectivityIT {

  private static final Logger log = LoggerFactory.getLogger(InformerConnectivityIT.class);

  public static final String NO_CR_ACCESS_USER = "noCRAccessUser";
  public static final String NO_CONFIG_MAP_ACCESS_USER = "noCMAccessUser";


  private String namespace;
  KubernetesClient globalClient = new KubernetesClientBuilder().build();

  @BeforeEach
  void setup(TestInfo testInfo) {
    namespace = testInfo.getTestMethod().orElseThrow().getName() + UUID.randomUUID();
    UnitTestUtils.applyCrd(globalClient, InformerConnectivityTestCustomResource.class);
    globalClient.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(namespace)
        .endMetadata().build()).create();
  }

  @AfterEach
  void tearDown() {
    globalClient.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(namespace)
        .endMetadata().build()).delete();
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(() -> assertThat(globalClient.namespaces().withName(namespace).get()).isNull());
  }

  @Test
  void notStartsIfNoCustomResourceRBACAccess() {
    KubernetesClient client = clientForUser(NO_CR_ACCESS_USER);

    Operator o = new Operator(client);
    o.register(new SimpleConnectivityTestCustomReconciler());

    assertThrows(OperatorException.class, o::start);
  }

  @Test
  void notStartsWhenNoAccessToSecondaryInformersResource() {
    applyRBACKRules("nocmaccessrole.yaml");
    KubernetesClient client = clientForUser(NO_CONFIG_MAP_ACCESS_USER);

    Operator o = new Operator(client);
    o.register(new InformerConnectivityTestCustomReconciler());

    assertThrows(OperatorException.class, o::start);
  }

  private void applyRBACKRules(String s) {

  }

  // todo add permission in runtime
  @Test
  void startsIfReconnectModeConfiguredAndNoCRAccess() {

  }

  @Test
  void startsIfReconnectModeConfiguredAndNoInformerResourceAccess() {

  }

  private KubernetesClient clientForUser(String user) {
    return new KubernetesClientBuilder()
            .withConfig(new ConfigBuilder()
                    .withImpersonateUsername(user)
                    .build())
            .build();
  }

}
