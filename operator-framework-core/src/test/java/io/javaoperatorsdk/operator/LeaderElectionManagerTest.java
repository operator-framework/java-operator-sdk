package io.javaoperatorsdk.operator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.fabric8.kubernetes.api.model.authorization.v1.ResourceRule;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectRulesReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SubjectRulesReviewStatus;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.client.Config;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

import static io.fabric8.kubernetes.client.Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_NAMESPACE_FILE;
import static io.javaoperatorsdk.operator.LeaderElectionManager.COORDINATION_GROUP;
import static io.javaoperatorsdk.operator.LeaderElectionManager.LEASES_RESOURCE;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderElectionManagerTest {

  private LeaderElectionManager leaderElectionManager(Object selfSubjectReview) {
    ControllerManager controllerManager = mock(ControllerManager.class);
    final var kubernetesClient = MockKubernetesClient.client(Lease.class, selfSubjectReview);
    when(kubernetesClient.getConfiguration()).thenReturn(Config.autoConfigure(null));
    var configurationService =
        ConfigurationService.newOverriddenConfigurationService(
            o ->
                o.withLeaderElectionConfiguration(new LeaderElectionConfiguration("test"))
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

    final var leaderElectionManager = leaderElectionManager(null);
    leaderElectionManager.start();
    assertTrue(leaderElectionManager.isLeaderElectionEnabled());
  }

  @Test
  void testFailedToInitInferLeaseNamespace() {
    System.setProperty(KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
    final var leaderElectionManager = leaderElectionManager(null);
    assertThrows(IllegalArgumentException.class, leaderElectionManager::start);
  }

  @Test
  void testInitPermissionsMultipleRulesWithResourceName(@TempDir Path tempDir) throws IOException {
    var namespace = "foo";
    var namespacePath = tempDir.resolve("namespace");
    Files.writeString(namespacePath, namespace);

    System.setProperty(KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
    System.setProperty(KUBERNETES_NAMESPACE_FILE, namespacePath.toString());

    SelfSubjectRulesReview review = new SelfSubjectRulesReview();
    review.setStatus(new SubjectRulesReviewStatus());
    var resourceRule1 = new ResourceRule();
    resourceRule1.setApiGroups(Arrays.asList(COORDINATION_GROUP));
    resourceRule1.setResources(Arrays.asList(LEASES_RESOURCE));
    resourceRule1.setResourceNames(Arrays.asList("test"));
    resourceRule1.setVerbs(Arrays.asList("get", "update"));
    var resourceRule2 = new ResourceRule();
    resourceRule2.setApiGroups(Arrays.asList(COORDINATION_GROUP));
    resourceRule2.setResources(Arrays.asList(LEASES_RESOURCE));
    resourceRule2.setVerbs(Arrays.asList("create"));
    review.getStatus().setResourceRules(Arrays.asList(resourceRule1, resourceRule2));

    final var leaderElectionManager = leaderElectionManager(review);
    leaderElectionManager.start();
    assertTrue(leaderElectionManager.isLeaderElectionEnabled());
  }

  @Test
  void testFailedToInitMissingPermission(@TempDir Path tempDir) throws IOException {
    var namespace = "foo";
    var namespacePath = tempDir.resolve("namespace");
    Files.writeString(namespacePath, namespace);

    System.setProperty(KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
    System.setProperty(KUBERNETES_NAMESPACE_FILE, namespacePath.toString());

    SelfSubjectRulesReview review = new SelfSubjectRulesReview();
    review.setStatus(new SubjectRulesReviewStatus());
    var resourceRule1 = new ResourceRule();
    resourceRule1.setApiGroups(Arrays.asList(COORDINATION_GROUP));
    resourceRule1.setResources(Arrays.asList(LEASES_RESOURCE));
    resourceRule1.setVerbs(Arrays.asList("get"));
    var resourceRule2 = new ResourceRule();
    resourceRule2.setApiGroups(Arrays.asList(COORDINATION_GROUP));
    resourceRule2.setResources(Arrays.asList(LEASES_RESOURCE));
    resourceRule2.setVerbs(Arrays.asList("update"));
    var resourceRule3 = new ResourceRule();
    resourceRule3.setApiGroups(Arrays.asList(COORDINATION_GROUP));
    resourceRule3.setResources(Arrays.asList(LEASES_RESOURCE));
    resourceRule3.setResourceNames(Arrays.asList("some-other-lease"));
    resourceRule3.setVerbs(Arrays.asList("create"));
    review.getStatus().setResourceRules(Arrays.asList(resourceRule1, resourceRule2, resourceRule3));

    final var leaderElectionManager = leaderElectionManager(review);
    assertThrows(OperatorException.class, leaderElectionManager::start);
  }
}
