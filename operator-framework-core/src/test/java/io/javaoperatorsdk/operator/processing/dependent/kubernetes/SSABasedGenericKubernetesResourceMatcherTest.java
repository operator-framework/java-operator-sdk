package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.DaemonSet;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SSABasedGenericKubernetesResourceMatcherTest {

  private final Context<?> mockedContext = mock();

  private final SSABasedGenericKubernetesResourceMatcher<HasMetadata> matcher =
      SSABasedGenericKubernetesResourceMatcher.getInstance();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    final var client = MockKubernetesClient.client(HasMetadata.class);
    when(mockedContext.getClient()).thenReturn(client);

    final var configurationService = mock(ConfigurationService.class);
    final var controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    when(controllerConfiguration.fieldManager()).thenReturn("controller");
    when(mockedContext.getControllerConfiguration()).thenReturn(controllerConfiguration);
  }

  @Test
  void checksIfAddsNotAddedByController() {
    var desired = loadResource("nginx-deployment.yaml", Deployment.class);
    var actual =
        loadResource("deployment-with-managed-fields-additional-controller.yaml", Deployment.class);

    assertThat(matcher.matches(actual, desired, mockedContext)).isTrue();
  }

  // In the example the owner reference in a list is referenced by "k:", while all the fields are
  // managed but not listed
  @Test
  void emptyListElementMatchesAllFields() {
    var desiredConfigMap = loadResource("configmap.empty-owner-reference-desired.yaml",
        ConfigMap.class);
    var actualConfigMap = loadResource("configmap.empty-owner-reference.yaml",
        ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isTrue();
  }

  // the whole "rules:" part is just implicitly managed
  @Test
  void wholeComplexFieldManaged() {
    var desiredConfigMap = loadResource("sample-whole-complex-part-managed-desired.yaml",
        ConfigMap.class);
    var actualConfigMap = loadResource("sample-whole-complex-part-managed.yaml",
        ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isTrue();
  }

  @Test
  void multiItemList() {
    var desiredConfigMap = loadResource("multi-container-pod-desired.yaml",
        ConfigMap.class);
    var actualConfigMap = loadResource("multi-container-pod.yaml",
        ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isTrue();
  }

  @Test
  void changeValueInDesiredMakesMatchFail() {
    var desiredConfigMap = loadResource("configmap.empty-owner-reference-desired.yaml",
        ConfigMap.class);
    desiredConfigMap.getData().put("key1", "different value");
    var actualConfigMap = loadResource("configmap.empty-owner-reference.yaml",
        ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isFalse();
  }

  @Test
  void changeValueActualMakesMatchFail() {
    var desiredConfigMap = loadResource("configmap.empty-owner-reference-desired.yaml",
        ConfigMap.class);

    var actualConfigMap = loadResource("configmap.empty-owner-reference.yaml",
        ConfigMap.class);
    actualConfigMap.getData().put("key1", "different value");

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isFalse();
  }

  @Test
  void addedLabelInDesiredMakesMatchFail() {
    var desiredConfigMap = loadResource("configmap.empty-owner-reference-desired.yaml",
        ConfigMap.class);
    desiredConfigMap.getMetadata().setLabels(Map.of("newlabel", "val"));

    var actualConfigMap = loadResource("configmap.empty-owner-reference.yaml",
        ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"sample-sts-volumeclaimtemplates-desired.yaml",
      "sample-sts-volumeclaimtemplates-desired-with-status.yaml",
      "sample-sts-volumeclaimtemplates-desired-with-volumemode.yaml"})
  void testSanitizeState_statefulSetWithVolumeClaims(String desiredResourceFileName) {
    var desiredStatefulSet = loadResource(desiredResourceFileName, StatefulSet.class);
    var actualStatefulSet = loadResource("sample-sts-volumeclaimtemplates.yaml",
        StatefulSet.class);

    assertThat(matcher.matches(actualStatefulSet, desiredStatefulSet, mockedContext)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"sample-sts-volumeclaimtemplates-desired-add.yaml",
      "sample-sts-volumeclaimtemplates-desired-update.yaml",
      "sample-sts-volumeclaimtemplates-desired-with-status-mismatch.yaml",
      "sample-sts-volumeclaimtemplates-desired-with-volumemode-mismatch.yaml"})
  void testSanitizeState_statefulSetWithVolumeClaims_withMismatch(String desiredResourceFileName) {
    var desiredStatefulSet = loadResource(desiredResourceFileName, StatefulSet.class);
    var actualStatefulSet = loadResource("sample-sts-volumeclaimtemplates.yaml",
        StatefulSet.class);

    assertThat(matcher.matches(actualStatefulSet, desiredStatefulSet, mockedContext)).isFalse();
  }

  @Test
  void testSanitizeState_statefulSetWithResources() {
    var desiredStatefulSet = loadResource("sample-sts-resources-desired.yaml", StatefulSet.class);
    var actualStatefulSet = loadResource("sample-sts-resources.yaml",
        StatefulSet.class);

    assertThat(matcher.matches(actualStatefulSet, desiredStatefulSet, mockedContext)).isTrue();
  }

  @Test
  void testSanitizeState_statefulSetWithResources_withMismatch() {
    var desiredStatefulSet =
        loadResource("sample-sts-resources-desired-update.yaml", StatefulSet.class);
    var actualStatefulSet = loadResource("sample-sts-resources.yaml",
        StatefulSet.class);

    assertThat(matcher.matches(actualStatefulSet, desiredStatefulSet, mockedContext)).isFalse();
  }

  @Test
  void testSanitizeState_replicaSetWithResources() {
    var desiredReplicaSet = loadResource("sample-rs-resources-desired.yaml", ReplicaSet.class);
    var actualReplicaSet = loadResource("sample-rs-resources.yaml",
        ReplicaSet.class);

    assertThat(matcher.matches(actualReplicaSet, desiredReplicaSet, mockedContext)).isTrue();
  }

  @Test
  void testSanitizeState_replicaSetWithResources_withMismatch() {
    var desiredReplicaSet =
        loadResource("sample-rs-resources-desired-update.yaml", ReplicaSet.class);
    var actualReplicaSet = loadResource("sample-rs-resources.yaml",
        ReplicaSet.class);

    assertThat(matcher.matches(actualReplicaSet, desiredReplicaSet, mockedContext)).isFalse();
  }

  @Test
  void testSanitizeState_daemonSetWithResources() {
    var desiredDaemonSet = loadResource("sample-ds-resources-desired.yaml", DaemonSet.class);
    var actualDaemonSet = loadResource("sample-ds-resources.yaml",
        DaemonSet.class);

    assertThat(matcher.matches(actualDaemonSet, desiredDaemonSet, mockedContext)).isTrue();
  }

  @Test
  void testSanitizeState_daemonSetWithResources_withMismatch() {
    var desiredDaemonSet =
        loadResource("sample-ds-resources-desired-update.yaml", DaemonSet.class);
    var actualDaemonSet = loadResource("sample-ds-resources.yaml",
        DaemonSet.class);

    assertThat(matcher.matches(actualDaemonSet, desiredDaemonSet, mockedContext)).isFalse();
  }

  private static <R> R loadResource(String fileName, Class<R> clazz) {
    return ReconcilerUtils.loadYaml(clazz, SSABasedGenericKubernetesResourceMatcherTest.class,
        fileName);
  }
}
