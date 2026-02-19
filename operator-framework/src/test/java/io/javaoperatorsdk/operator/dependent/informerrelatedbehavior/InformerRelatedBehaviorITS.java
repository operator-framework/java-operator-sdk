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
package io.javaoperatorsdk.operator.dependent.informerrelatedbehavior;

import java.time.Duration;

import org.junit.jupiter.api.*;

import io.fabric8.kubeapitest.junit.EnableKubeAPIServer;
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
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.health.InformerHealthIndicator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;

import static io.javaoperatorsdk.operator.dependent.informerrelatedbehavior.InformerRelatedBehaviorTestReconciler.CONFIG_MAP_DEPENDENT_RESOURCE;
import static io.javaoperatorsdk.operator.dependent.informerrelatedbehavior.InformerRelatedBehaviorTestReconciler.INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The test relies on a special api server configuration: "min-request-timeout" to have a very low
 * value (in case want to try with minikube use: "minikube start
 * --extra-config=apiserver.min-request-timeout=1")
 *
 * <p>This is important when tests are affected by permission changes, since the watch permissions
 * are just checked when established a watch request. So minimal request timeout is set to make sure
 * that with periodical watch reconnect the permission is tested again.
 *
 * <p>The test ends with "ITS" (Special) since it needs to run separately from other ITs
 */
@EnableKubeAPIServer(
    apiServerFlags = {"--min-request-timeout", "1"},
    updateKubeConfigFile = true)
class InformerRelatedBehaviorITS {

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
    LocallyRunOperatorExtension.applyCrd(
        InformerRelatedBehaviorTestCustomResource.class, adminClient);
    testInfo
        .getTestMethod()
        .ifPresent(
            method -> {
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
      operator.stop();
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
        operator
            .getRuntimeInfo()
            .unhealthyInformerWrappingEventSourceHealthIndicator()
            .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);

    InformerHealthIndicator controllerHealthIndicator =
        (InformerHealthIndicator)
            unhealthyEventSources
                .get(ControllerEventSource.NAME)
                .informerHealthIndicators()
                .get(additionalNamespace);
    assertThat(controllerHealthIndicator).isNotNull();
    assertThat(controllerHealthIndicator.getTargetNamespace()).isEqualTo(additionalNamespace);
    assertThat(controllerHealthIndicator.isWatching()).isFalse();

    InformerHealthIndicator configMapHealthIndicator =
        (InformerHealthIndicator)
            unhealthyEventSources
                .get(InformerRelatedBehaviorTestReconciler.CONFIG_MAP_DEPENDENT_RESOURCE)
                .informerHealthIndicators()
                .get(additionalNamespace);
    assertThat(configMapHealthIndicator).isNotNull();
    assertThat(configMapHealthIndicator.getTargetNamespace()).isEqualTo(additionalNamespace);
    assertThat(configMapHealthIndicator.isWatching()).isFalse();
  }

  // this will be investigated separately under the issue below, it's not crucial functional wise,
  // it is rather "something working why it should", not other way around; but it's not a
  // showstopper
  // https://github.com/operator-framework/java-operator-sdk/issues/1835
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

    await()
        .pollDelay(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              var cm =
                  adminClient
                      .configMaps()
                      .inNamespace(actualNamespace)
                      .withName(TEST_RESOURCE_NAME)
                      .get();
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
    await()
        .pollDelay(Duration.ofMillis(2000))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isEqualTo(0);
            });
  }

  InformerRelatedBehaviorTestCustomResource testCustomResource() {
    InformerRelatedBehaviorTestCustomResource testCustomResource =
        new InformerRelatedBehaviorTestCustomResource();
    testCustomResource.setMetadata(
        new ObjectMetaBuilder()
            .withNamespace(actualNamespace)
            .withName(TEST_RESOURCE_NAME)
            .build());
    return testCustomResource;
  }

  private ConfigMap dependentConfigMap() {
    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(TEST_RESOURCE_NAME)
                .withNamespace(actualNamespace)
                .build())
        .build();
  }

  private void assertReconciled() {
    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(0);
              var cm =
                  adminClient
                      .configMaps()
                      .inNamespace(actualNamespace)
                      .withName(TEST_RESOURCE_NAME)
                      .get();
              assertThat(cm).isNotNull();
            });
  }

  @SuppressWarnings("unchecked")
  private void assertRuntimeInfoNoCRPermission(Operator operator) {
    assertThat(operator.getRuntimeInfo().allEventSourcesAreHealthy()).isFalse();
    var unhealthyEventSources =
        operator
            .getRuntimeInfo()
            .unhealthyEventSources()
            .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);
    assertThat(unhealthyEventSources).isNotEmpty();
    assertThat(unhealthyEventSources.get(ControllerEventSource.NAME))
        .isNotNull();
    var informerHealthIndicators =
        operator
            .getRuntimeInfo()
            .unhealthyInformerWrappingEventSourceHealthIndicator()
            .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);
    assertThat(informerHealthIndicators).isNotEmpty();
    assertThat(
            informerHealthIndicators
                .get(ControllerEventSource.NAME)
                .informerHealthIndicators())
        .hasSize(1);
  }

  @SuppressWarnings("unchecked")
  private void assertRuntimeInfoForSecondaryPermission(Operator operator) {
    assertThat(operator.getRuntimeInfo().allEventSourcesAreHealthy()).isFalse();
    var unhealthyEventSources =
        operator
            .getRuntimeInfo()
            .unhealthyEventSources()
            .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);
    assertThat(unhealthyEventSources).isNotEmpty();
    assertThat(unhealthyEventSources.get(CONFIG_MAP_DEPENDENT_RESOURCE)).isNotNull();
    var informerHealthIndicators =
        operator
            .getRuntimeInfo()
            .unhealthyInformerWrappingEventSourceHealthIndicator()
            .get(INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER);
    assertThat(informerHealthIndicators).isNotEmpty();
    assertThat(
            informerHealthIndicators.get(CONFIG_MAP_DEPENDENT_RESOURCE).informerHealthIndicators())
        .hasSize(1);
  }

  KubernetesClient clientUsingServiceAccount() {
    KubernetesClient client =
        new KubernetesClientBuilder()
            .withConfig(
                new ConfigBuilder()
                    .withImpersonateUsername("rbac-test-user")
                    .withNamespace(actualNamespace)
                    .build())
            .build();
    return client;
  }

  Operator startOperator(boolean stopOnInformerErrorDuringStartup) {
    return startOperator(stopOnInformerErrorDuringStartup, true);
  }

  Operator startOperator(
      boolean stopOnInformerErrorDuringStartup, boolean addStopHandler, String... namespaces) {

    reconciler = new InformerRelatedBehaviorTestReconciler();

    Operator operator =
        new Operator(
            co -> {
              co.withKubernetesClient(clientUsingServiceAccount());
              co.withStopOnInformerErrorDuringStartup(stopOnInformerErrorDuringStartup);
              co.withCacheSyncTimeout(Duration.ofMillis(3000));
              co.withReconciliationTerminationTimeout(Duration.ofSeconds(1));
              if (addStopHandler) {
                co.withInformerStoppedHandler(
                    (informer, ex) -> replacementStopHandlerCalled = true);
              }
            });
    operator.register(
        reconciler,
        o -> {
          if (namespaces.length > 0) {
            o.settingNamespaces(namespaces);
          }
        });
    operator.start();
    return operator;
  }

  private void setNoConfigMapAccess() {
    applyClusterRole("rbac-test-no-configmap-access.yaml");
    applyClusterRoleBinding();
  }

  private void setNoCustomResourceAccess() {
    applyClusterRole("rbac-test-no-cr-access.yaml");
    applyClusterRoleBinding();
  }

  private void setFullResourcesAccess() {
    applyClusterRole("rbac-test-full-access-role.yaml");
    applyClusterRoleBinding();
  }

  private void addRoleBindingsToTestNamespaces() {
    var role =
        ReconcilerUtilsInternal.loadYaml(
            Role.class, this.getClass(), "rbac-test-only-main-ns-access.yaml");
    adminClient.resource(role).inNamespace(actualNamespace).createOrReplace();
    var roleBinding =
        ReconcilerUtilsInternal.loadYaml(
            RoleBinding.class, this.getClass(), "rbac-test-only-main-ns-access-binding.yaml");
    adminClient.resource(roleBinding).inNamespace(actualNamespace).createOrReplace();
  }

  private void applyClusterRoleBinding() {
    var clusterRoleBinding =
        ReconcilerUtilsInternal.loadYaml(
            ClusterRoleBinding.class, this.getClass(), "rbac-test-role-binding.yaml");
    adminClient.resource(clusterRoleBinding).createOrReplace();
  }

  private void applyClusterRole(String filename) {
    var clusterRole =
        ReconcilerUtilsInternal.loadYaml(ClusterRole.class, this.getClass(), filename);
    adminClient.resource(clusterRole).createOrReplace();
  }

  private Namespace namespace() {
    return namespace(actualNamespace);
  }

  private Namespace namespace(String name) {
    Namespace n = new Namespace();
    n.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return n;
  }

  private void removeClusterRoleBinding() {
    var clusterRoleBinding =
        ReconcilerUtilsInternal.loadYaml(
            ClusterRoleBinding.class, this.getClass(), "rbac-test-role-binding.yaml");
    adminClient.resource(clusterRoleBinding).delete();
  }
}
