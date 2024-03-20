package io.javaoperatorsdk.operator;

import static io.fabric8.kubernetes.client.Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_NAMESPACE_FILE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.client.Config;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeaderElectionManagerTest {

  private LeaderElectionManager leaderElectionManager() {
    ControllerManager controllerManager = mock(ControllerManager.class);
    final var kubernetesClient = MockKubernetesClient.client(Lease.class);
    when(kubernetesClient.getConfiguration()).thenReturn(Config.autoConfigure(null));
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            o -> o.withLeaderElectionConfiguration(new LeaderElectionConfiguration("test"))
                .withKubernetesClient(kubernetesClient));
    return new LeaderElectionManager(controllerManager, configurationService);
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

    final var leaderElectionManager = leaderElectionManager();
    leaderElectionManager.start();
    assertTrue(leaderElectionManager.isLeaderElectionEnabled());
  }

  @Test
  void testFailedToInitInferLeaseNamespace() {
    System.setProperty(KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
    assertThrows(IllegalArgumentException.class, () -> leaderElectionManager().start());
  }
}
