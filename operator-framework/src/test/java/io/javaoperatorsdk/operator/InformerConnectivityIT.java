package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.junit.UnitTestUtils;
import io.javaoperatorsdk.operator.sample.informerconnectivity.InformerConnectivityTestCustomReconciler;
import io.javaoperatorsdk.operator.sample.informerconnectivity.InformerConnectivityTestCustomResource;
import io.javaoperatorsdk.operator.sample.informerconnectivity.SimpleConnectivityTestCustomReconciler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InformerConnectivityIT {

  private static final Logger log = LoggerFactory.getLogger(InformerConnectivityIT.class);

  public static final String NO_CR_ACCESS_USER = "noCRAccessUser";
  public static final String CR_ACCESS_USER = "CRAccessUser";
  public static final String CR_RBAC = "craccessrole.yaml";
  public static final String CR_ACCESS_ROLE_BINDING_NAME = "craccess";

  private String namespace;

  KubernetesClient globalClient = new KubernetesClientBuilder().build();

  @BeforeEach
  void setup(TestInfo testInfo) {
    namespace = UnitTestUtils.generateNamespaceForTestName(testInfo);
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
        .untilAsserted(
            () -> assertThat(globalClient.namespaces().withName(namespace).get()).isNull());
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
    applyRBACKRules(CR_RBAC);
    KubernetesClient client = clientForUser(CR_ACCESS_USER);

    Operator o = new Operator(client);
    o.register(new InformerConnectivityTestCustomReconciler(),
        configOverride -> configOverride.settingNamespaces(namespace));

    assertThrows(OperatorException.class, o::start);
  }

  @Test
  void stopsIfAccessRemovedToCR() throws InterruptedException {
    applyRBACKRules(CR_RBAC);
    KubernetesClient client = clientForUser(CR_ACCESS_USER);

    Operator o = new Operator(client);
    o.register(new SimpleConnectivityTestCustomReconciler(),
            configOverride -> configOverride.settingNamespaces(namespace));
    o.start();

    removeAccessToCR();

    Thread.sleep(5000);
  }
  
  @Test
  void stopsIfAccessRemovedToInformerResource() {

  }

  @Test
  void startsIfReconnectModeConfiguredAndNoCRAccess() {

  }

  @Test
  void startsIfReconnectModeConfiguredAndNoInformerResourceAccess() {

  }

  @Test
  void continuesToInReconnectModeWorkIfAccessRemovedAndAddedForCR() {

  }

  @Test
  void continuesToInReconnectModeWorkIfAccessRemovedAndAddedForInformerResource() {
  }

  private void removeAccessToCR() {
    globalClient.rbac().roleBindings().
            inNamespace(namespace).withName(CR_ACCESS_ROLE_BINDING_NAME).delete();
  }

  private void applyRBACKRules(String fileName) {
    try (InputStream is =
        InformerConnectivityTestCustomResource.class.getResourceAsStream(fileName)) {
      var list = globalClient.load(is).get();
      globalClient.resourceList(list).inNamespace(namespace).create();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private KubernetesClient clientForUser(String user) {
    return new KubernetesClientBuilder()
        .withConfig(new ConfigBuilder()
            .withImpersonateUsername(user)
            .build())
        .build();
  }

}
