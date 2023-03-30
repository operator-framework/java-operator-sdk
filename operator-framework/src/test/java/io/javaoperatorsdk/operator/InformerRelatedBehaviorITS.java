package io.javaoperatorsdk.operator;

import java.time.Duration;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.javaoperatorsdk.jenvtest.junit.EnableKubeAPIServer;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.health.InformerHealthIndicator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.sample.informerrelatedbehavior.ConfigMapDependentResource;
import io.javaoperatorsdk.operator.sample.informerrelatedbehavior.InformerRelatedBehaviorTestCustomResource;
import io.javaoperatorsdk.operator.sample.informerrelatedbehavior.InformerRelatedBehaviorTestReconciler;

import static io.javaoperatorsdk.operator.sample.informerrelatedbehavior.InformerRelatedBehaviorTestReconciler.CONFIG_MAP_DEPENDENT_RESOURCE;
import static io.javaoperatorsdk.operator.sample.informerrelatedbehavior.InformerRelatedBehaviorTestReconciler.INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The test relies on a special api server configuration: "min-request-timeout" to have a very low
 * value (in case want to try with minikube use: "minikube start
 * --extra-config=apiserver.min-request-timeout=1")
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
@EnableKubeAPIServer(apiServerFlags = {"--min-request-timeout", "1"})
class InformerRelatedBehaviorITS {

  private static final Logger log = LoggerFactory.getLogger(InformerRelatedBehaviorITS.class);

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String ADDITIONAL_NAMESPACE_SUFFIX = "-additional";

  KubernetesClient adminClient = new KubernetesClientBuilder().build();
  InformerRelatedBehaviorTestReconciler reconciler;
  String actualNamespace;
  String additionalNamespace;
  Operator operator;
  volatile boolean replacementStopHandlerCalled = false;

  @BeforeEach
  void beforeEach(TestInfo testInfo) {
    LocallyRunOperatorExtension.applyCrd(InformerRelatedBehaviorTestCustomResource.class,
        adminClient);
    testInfo.getTestMethod().ifPresent(method -> {
      actualNamespace = KubernetesResourceUtil.sanitizeName(method.getName());
      additionalNamespace = actualNamespace + ADDITIONAL_NAMESPACE_SUFFIX;
      adminClient.resource(namespace()).createOrReplace();
    });
    // cleans up binding before test, not all test cases use cluster role
    removeClusterRoleBinding();
  }

  @AfterEach
  void cleanup() {
    if (operator != null) {
      operator.stop(Duration.ofSeconds(1));
    }
    adminClient.resource(dependentConfigMap()).delete();
    adminClient.resource(testCustomResource()).delete();
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

    operator = startOperator(false);
    assertNotReconciled();
    assertRuntimeInfoNoCRPermission(operator);

    setFullResourcesAccess();
    waitForWatchReconnect();
    assertReconciled();
    assertThat(operator.getRuntimeInfo().allEventSourcesAreHealthy()).isTrue();
  }


  @Test
  void startsUpWhenNoPermissionToSecondaryResource() {
    adminClient.resource(testCustomResource()).createOrReplace();
    setNoConfigMapAccess();

    operator = startOperator(false);
    assertNotReconciled();
    assertRuntimeInfoForSecondaryPermission(operator);

    setFullResourcesAccess();
    waitForWatchReconnect();
    assertReconciled();
  }

  @Test
  void startsUpIfNoPermissionToOneOfTwoNamespaces() {
    adminClient.resource(namespace(additionalNamespace)).createOrReplace();

    addRoleBindingsToTestNamespaces();
    operator = startOperator(false, false, actualNamespace, additionalNamespace);
    assertInformerNotWatchingForAdditionalNamespace(operator);

    adminClient.resource(testCustomResource()).createOrReplace();
    waitForWatchReconnect();
    assertReconciled();
  }

  private void assertInformerNotWatchingForAdditionalNamespace(Operator operator) {
    assertThat(operator.getRuntimeInfo().allEventSourcesAreHealthy()).isFalse();
    var unhealthyEventSources =
        operator.getRuntimeInfo().unhealthyInformerWrappingEventSourceHealthIndicator()
            .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);

    InformerHealthIndicator controllerHealthIndicator =
        (InformerHealthIndicator) unhealthyEventSources
            .get(ControllerResourceEventSource.class.getSimpleName())
            .informerHealthIndicators().get(additionalNamespace);
    assertThat(controllerHealthIndicator).isNotNull();
    assertThat(controllerHealthIndicator.getTargetNamespace()).isEqualTo(additionalNamespace);
    assertThat(controllerHealthIndicator.isWatching()).isFalse();

    InformerHealthIndicator configMapHealthIndicator =
        (InformerHealthIndicator) unhealthyEventSources
            .get(ConfigMapDependentResource.class.getSimpleName())
            .informerHealthIndicators().get(additionalNamespace);
    assertThat(configMapHealthIndicator).isNotNull();
    assertThat(configMapHealthIndicator.getTargetNamespace()).isEqualTo(additionalNamespace);
    assertThat(configMapHealthIndicator.isWatching()).isFalse();
  }


  // this will be investigated separately under the issue below, it's not crucial functional wise,
  // it is rather "something working why it should", not other way around; but it's not a
  // showstopper
  // https://github.com/java-operator-sdk/java-operator-sdk/issues/1835
  @Disabled
  @Test
  void resilientForLoosingPermissionForCustomResource() {
    setFullResourcesAccess();
    operator = startOperator(true);
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

    await().untilAsserted(() -> assertThat(replacementStopHandlerCalled).isTrue());
  }

  @Test
  void notExitingWithDefaultStopHandlerIfErrorHappensOnStartup() {
    setNoCustomResourceAccess();
    adminClient.resource(testCustomResource()).createOrReplace();

    assertThrows(OperatorException.class, () -> startOperator(true, false));

    // note that we just basically check here that the default handler does not call system exit.
    // Thus, the test does not terminate before to assert.
    await().untilAsserted(() -> assertThat(replacementStopHandlerCalled).isFalse());
  }

  private static void waitForWatchReconnect() {
    try {
      Thread.sleep(5000);
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

  private ConfigMap dependentConfigMap() {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(actualNamespace)
            .build())
        .build();
  }

  private void assertReconciled() {
    await().untilAsserted(() -> {
      assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(0);
      var cm =
          adminClient.configMaps().inNamespace(actualNamespace).withName(TEST_RESOURCE_NAME).get();
      assertThat(cm).isNotNull();
    });
  }

  @SuppressWarnings("unchecked")
  private void assertRuntimeInfoNoCRPermission(Operator operator) {
    assertThat(operator.getRuntimeInfo().allEventSourcesAreHealthy()).isFalse();
    var unhealthyEventSources =
        operator.getRuntimeInfo().unhealthyEventSources()
            .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);
    assertThat(unhealthyEventSources).isNotEmpty();
    assertThat(unhealthyEventSources.get(ControllerResourceEventSource.class.getSimpleName()))
        .isNotNull();
    var informerHealthIndicators = operator.getRuntimeInfo()
        .unhealthyInformerWrappingEventSourceHealthIndicator()
        .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);
    assertThat(informerHealthIndicators).isNotEmpty();
    assertThat(informerHealthIndicators.get(ControllerResourceEventSource.class.getSimpleName())
        .informerHealthIndicators())
        .hasSize(1);
  }

  @SuppressWarnings("unchecked")
  private void assertRuntimeInfoForSecondaryPermission(Operator operator) {
    assertThat(operator.getRuntimeInfo().allEventSourcesAreHealthy()).isFalse();
    var unhealthyEventSources =
        operator.getRuntimeInfo().unhealthyEventSources()
            .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);
    assertThat(unhealthyEventSources).isNotEmpty();
    assertThat(unhealthyEventSources.get(CONFIG_MAP_DEPENDENT_RESOURCE)).isNotNull();
    var informerHealthIndicators = operator.getRuntimeInfo()
        .unhealthyInformerWrappingEventSourceHealthIndicator()
        .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);
    assertThat(informerHealthIndicators).isNotEmpty();
    assertThat(
        informerHealthIndicators.get(CONFIG_MAP_DEPENDENT_RESOURCE).informerHealthIndicators())
        .hasSize(1);
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
    return startOperator(stopOnInformerErrorDuringStartup, true);
  }

  Operator startOperator(boolean stopOnInformerErrorDuringStartup, boolean addStopHandler,
      String... namespaces) {
    ConfigurationServiceProvider.reset();
    reconciler = new InformerRelatedBehaviorTestReconciler();

    Operator operator = new Operator(clientUsingServiceAccount(),
        co -> {
          co.withStopOnInformerErrorDuringStartup(stopOnInformerErrorDuringStartup);
          co.withCacheSyncTimeout(Duration.ofMillis(3000));
          if (addStopHandler) {
            co.withInformerStoppedHandler((informer, ex) -> replacementStopHandlerCalled = true);
          }
        });
    operator.register(reconciler, o -> {
      if (namespaces.length > 0) {
        o.settingNamespaces(namespaces);
      }
    });
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

  private void addRoleBindingsToTestNamespaces() {
    var role = ReconcilerUtils
        .loadYaml(Role.class, this.getClass(), "rback-test-only-main-ns-access.yaml");
    adminClient.resource(role).inNamespace(actualNamespace).createOrReplace();
    var roleBinding = ReconcilerUtils
        .loadYaml(RoleBinding.class, this.getClass(),
            "rback-test-only-main-ns-access-binding.yaml");
    adminClient.resource(roleBinding).inNamespace(actualNamespace).createOrReplace();
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
    return namespace(actualNamespace);
  }

  private Namespace namespace(String name) {
    Namespace n = new Namespace();
    n.setMetadata(new ObjectMetaBuilder()
        .withName(name)
        .build());
    return n;
  }

  private void removeClusterRoleBinding() {
    var clusterRoleBinding = ReconcilerUtils
        .loadYaml(ClusterRoleBinding.class, this.getClass(), "rback-test-role-binding.yaml");
    adminClient.resource(clusterRoleBinding).delete();
  }
}
