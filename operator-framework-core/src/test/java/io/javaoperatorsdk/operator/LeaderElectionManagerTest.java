package io.javaoperatorsdk.operator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

import static io.fabric8.kubernetes.client.Config.KUBERNETES_NAMESPACE_FILE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LeaderElectionManagerTest {

  private ControllerManager controllerManager;
  private KubernetesClient kubernetesClient;
  private LeaderElectionManager leaderElectionManager;

  @BeforeEach
  void setUp() {
    controllerManager = mock(ControllerManager.class);
    kubernetesClient = mock(KubernetesClient.class);
    leaderElectionManager = new LeaderElectionManager(controllerManager);
  }

  @AfterEach
  void tearDown() {
    System.getProperties().remove(KUBERNETES_NAMESPACE_FILE);
  }

  @Test
  void testInit() {
    leaderElectionManager.init(new LeaderElectionConfiguration("test", "testns"), kubernetesClient);
    assertTrue(leaderElectionManager.isLeaderElectionEnabled());
  }

  @Test
  void testInitInferLeaseNamespace(@TempDir Path tempDir) throws IOException {
    var namespace = "foo";
    var namespacePath = tempDir.resolve("namespace");
    Files.writeString(namespacePath, namespace);

    System.setProperty(KUBERNETES_NAMESPACE_FILE, namespacePath.toString());

    leaderElectionManager.init(new LeaderElectionConfiguration("test"), kubernetesClient);
    assertTrue(leaderElectionManager.isLeaderElectionEnabled());
  }

  @Test
  void testFailedToInitInferLeaseNamespace() {
    assertThrows(
        IllegalArgumentException.class,
        () -> leaderElectionManager.init(new LeaderElectionConfiguration("test"),
            kubernetesClient));
  }
}
