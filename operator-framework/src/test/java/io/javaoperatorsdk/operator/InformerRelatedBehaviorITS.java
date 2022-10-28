package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.*;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.informerrelatedbehavior.InformerRelatedBehaviorTestCustomResource;
import io.javaoperatorsdk.operator.sample.informerrelatedbehavior.InformerRelatedBehaviorTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The test relies on a special minikube configuration: "min-request-timeout" to have a very low
 * value, see: "minikube start --extra-config=apiserver.min-request-timeout=3"
 *
 * <p>
 * This is important when tests are affected by permission changes, since the watch permissions are
 * just checked when established a watch request. So minimal request timeout is set to make sure
 * that with periodical watch reconnect the permission is tested again.
 * </p>
 * <p>
 * The test ends with "ITS" (Special) since it needs to run separately from other ITs
 * </p>
 */
class InformerRelatedBehaviorITS {

  public static final String TEST_RESOURCE_NAME = "test1";

  KubernetesClient adminClient = new KubernetesClientBuilder().build();
  InformerRelatedBehaviorTestReconciler reconciler;
  String actualNamespace;
  volatile boolean stopHandlerCalled = false;

  @BeforeEach
  void beforeEach(TestInfo testInfo) {
    LocallyRunOperatorExtension.applyCrd(InformerRelatedBehaviorTestCustomResource.class,
        adminClient);
    testInfo.getTestMethod().ifPresent(method -> {
      actualNamespace = KubernetesResourceUtil.sanitizeName(method.getName());
      adminClient.resource(namespace()).createOrReplace();
    });
  }

  @AfterEach
  void cleanup() {
    adminClient.resource(testCustomResource()).delete();
    adminClient.resource(namespace()).delete();
  }

  @Test
  void notStartsUpWithoutPermissionIfInstructed() {
    adminClient.resource(testCustomResource()).createOrReplace();
    setNoCustomResourceAccess();

    assertThrows(OperatorException.class, () -> startOperator(true));
    assertNotReconciled();
  }

  @Test
  void startsUpWhenNoPermissionToCustomResource() {
    adminClient.resource(testCustomResource()).createOrReplace();
    setNoCustomResourceAccess();

    startOperator(false);
    assertNotReconciled();

    setFullResourcesAccess();
    waitForWatchReconnect();
    assertReconciled();
  }

  @Test
  void startsUpWhenNoPermissionToSecondaryResource() {
    adminClient.resource(testCustomResource()).createOrReplace();
    setNoConfigMapAccess();

    startOperator(false);
    assertNotReconciled();

    setFullResourcesAccess();
    waitForWatchReconnect();
    assertReconciled();
  }

  @Test
  void resilientForLoosingPermissionForCustomResource() throws InterruptedException {
    setFullResourcesAccess();
    startOperator(true);
    setNoCustomResourceAccess();

    waitForWatchReconnect();
    adminClient.resource(testCustomResource()).createOrReplace();

    assertNotReconciled();

    setFullResourcesAccess();
    assertReconciled();
  }


  @Test
  void resilientForLoosingPermissionForSecondaryResource() {
    setFullResourcesAccess();
    startOperator(true);
    setNoConfigMapAccess();

    waitForWatchReconnect();
    adminClient.resource(testCustomResource()).createOrReplace();

    await().pollDelay(Duration.ofMillis(300)).untilAsserted(() -> {
      var cm =
          adminClient.configMaps().inNamespace(actualNamespace).withName(TEST_RESOURCE_NAME).get();
      assertThat(cm).isNull();
    });

    setFullResourcesAccess();
    assertReconciled();
  }

  @Test
  void callsStopHandlerOnStartupFail() {
    setNoCustomResourceAccess();
    adminClient.resource(testCustomResource()).createOrReplace();

    assertThrows(OperatorException.class, () -> startOperator(true));

    await().untilAsserted(() -> assertThat(stopHandlerCalled).isTrue());
  }

  private static void waitForWatchReconnect() {
    try {
      Thread.sleep(6000);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  private void assertNotReconciled() {
    await().pollDelay(Duration.ofMillis(2000)).untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isEqualTo(0);
    });
  }

  InformerRelatedBehaviorTestCustomResource testCustomResource() {
    InformerRelatedBehaviorTestCustomResource testCustomResource =
        new InformerRelatedBehaviorTestCustomResource();
    testCustomResource.setMetadata(new ObjectMetaBuilder()
        .withNamespace(actualNamespace)
        .withName(TEST_RESOURCE_NAME)
        .build());
    return testCustomResource;
  }

  private void assertReconciled() {
    await().untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(0);
      var cm =
          adminClient.configMaps().inNamespace(actualNamespace).withName(TEST_RESOURCE_NAME).get();
      assertThat(cm).isNotNull();
    });
  }

  KubernetesClient clientUsingServiceAccount() {
    KubernetesClient client = new KubernetesClientBuilder()
        .withConfig(new ConfigBuilder()
            .withImpersonateUsername("rbac-test-user")
            .withNamespace(actualNamespace)
            .build())
        .build();
    return client;
  }

  Operator startOperator(boolean stopOnInformerErrorDuringStartup) {
    ConfigurationServiceProvider.reset();
    reconciler = new InformerRelatedBehaviorTestReconciler();

    Operator operator = new Operator(clientUsingServiceAccount(),
        co -> {
          co.withStopOnInformerErrorDuringStartup(stopOnInformerErrorDuringStartup);
          co.withCacheSyncTimeout(Duration.ofMillis(3000));
          co.withInformerStoppedHandler((informer, ex) -> {
            stopHandlerCalled = true;
          });
        });
    operator.register(reconciler);
    operator.installShutdownHook();
    operator.start();
    return operator;
  }

  private void setNoConfigMapAccess() {
    applyClusterRole("rback-test-no-configmap-access.yaml");
    applyClusterRoleBinding();
  }

  private void setNoCustomResourceAccess() {
    applyClusterRole("rback-test-no-cr-access.yaml");
    applyClusterRoleBinding();
  }

  private void setFullResourcesAccess() {
    applyClusterRole("rback-test-full-access-role.yaml");
    applyClusterRoleBinding();
  }

  private void applyClusterRoleBinding() {
    var clusterRoleBinding = ReconcilerUtils
        .loadYaml(ClusterRoleBinding.class, this.getClass(), "rback-test-role-binding.yaml");
    adminClient.resource(clusterRoleBinding).createOrReplace();
  }

  private void applyClusterRole(String filename) {
    var clusterRole = ReconcilerUtils
        .loadYaml(ClusterRole.class, this.getClass(), filename);
    adminClient.resource(clusterRole).createOrReplace();
  }

  private Namespace namespace() {
    Namespace n = new Namespace();
    n.setMetadata(new ObjectMetaBuilder()
        .withName(actualNamespace)
        .build());
    return n;
  }
}
