package io.javaoperatorsdk.operator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

import static io.fabric8.kubernetes.client.Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_NAMESPACE_FILE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LeaderElectionManagerTest {

  private LeaderElectionManager leaderElectionManager;

  @BeforeEach
  void setUp() {
    ControllerManager controllerManager = mock(ControllerManager.class);
    final var kubernetesClient = mock(KubernetesClient.class);
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(new BaseConfigurationService(),
            o -> o.withLeaderElectionConfiguration(new LeaderElectionConfiguration("test")));
    leaderElectionManager =
        new LeaderElectionManager(kubernetesClient, controllerManager, configurationService);
  }

  @AfterEach
  void tearDown() {
    System.getProperties().remove(KUBERNETES_NAMESPACE_FILE);
    System.getProperties().remove(KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY);
  }

  @Test
  void testInitInferLeaseNamespace(@TempDir Path tempDir) throws IOException {
    var namespace = "foo";
    var namespacePath = tempDir.resolve("namespace");
    Files.writeString(namespacePath, namespace);

    System.setProperty(KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
    System.setProperty(KUBERNETES_NAMESPACE_FILE, namespacePath.toString());

    leaderElectionManager.start();
    assertTrue(leaderElectionManager.isLeaderElectionEnabled());
  }

  @Test
  void testFailedToInitInferLeaseNamespace() {
    System.setProperty(KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
    assertThrows(
        IllegalArgumentException.class,
        () -> leaderElectionManager.start());
  }
}
