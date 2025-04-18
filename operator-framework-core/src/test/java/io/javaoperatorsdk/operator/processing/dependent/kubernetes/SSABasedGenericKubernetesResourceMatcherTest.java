package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.HashMap;
import java.util.List;
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
    var desiredConfigMap =
        loadResource("configmap.empty-owner-reference-desired.yaml", ConfigMap.class);
    var actualConfigMap = loadResource("configmap.empty-owner-reference.yaml", ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isTrue();
  }

  // the whole "rules:" part is just implicitly managed
  @Test
  void wholeComplexFieldManaged() {
    var desiredConfigMap =
        loadResource("sample-whole-complex-part-managed-desired.yaml", ConfigMap.class);
    var actualConfigMap = loadResource("sample-whole-complex-part-managed.yaml", ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isTrue();
  }

  @Test
  void multiItemList() {
    var desiredConfigMap = loadResource("multi-container-pod-desired.yaml", ConfigMap.class);
    var actualConfigMap = loadResource("multi-container-pod.yaml", ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isTrue();
  }

  @Test
  void changeValueInDesiredMakesMatchFail() {
    var desiredConfigMap =
        loadResource("configmap.empty-owner-reference-desired.yaml", ConfigMap.class);
    desiredConfigMap.getData().put("key1", "different value");
    var actualConfigMap = loadResource("configmap.empty-owner-reference.yaml", ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isFalse();
  }

  @Test
  void changeValueActualMakesMatchFail() {
    var desiredConfigMap =
        loadResource("configmap.empty-owner-reference-desired.yaml", ConfigMap.class);

    var actualConfigMap = loadResource("configmap.empty-owner-reference.yaml", ConfigMap.class);
    actualConfigMap.getData().put("key1", "different value");

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isFalse();
  }

  @Test
  void addedLabelInDesiredMakesMatchFail() {
    var desiredConfigMap =
        loadResource("configmap.empty-owner-reference-desired.yaml", ConfigMap.class);
    desiredConfigMap.getMetadata().setLabels(Map.of("newlabel", "val"));

    var actualConfigMap = loadResource("configmap.empty-owner-reference.yaml", ConfigMap.class);

    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void sortListItemsTest() {
    var nestedMap1 = new HashMap<String, Object>();
    nestedMap1.put("z", 26);
    nestedMap1.put("y", 25);

    var nestedMap2 = new HashMap<String, Object>();
    nestedMap2.put("b", 26);
    nestedMap2.put("c", 25);
    nestedMap2.put("a", 24);

    var unsortedListItems = List.<Object>of(1, nestedMap1, nestedMap2);
    var sortedListItems = matcher.sortListItems(unsortedListItems);
    assertThat(sortedListItems).element(0).isEqualTo(1);

    var sortedNestedMap1 = (Map<String, Object>) sortedListItems.get(1);
    assertThat(sortedNestedMap1.keySet()).containsExactly("y", "z");

    var sortedNestedMap2 = (Map<String, Object>) sortedListItems.get(2);
    assertThat(sortedNestedMap2.keySet()).containsExactly("a", "b", "c");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSortMapWithNestedMap() {
    var nestedMap = new HashMap<String, Object>();
    nestedMap.put("z", 26);
    nestedMap.put("y", 25);

    var unsortedMap = new HashMap<String, Object>();
    unsortedMap.put("b", nestedMap);
    unsortedMap.put("a", 1);
    unsortedMap.put("c", 2);

    var sortedMap = matcher.sortMap(unsortedMap);
    assertThat(sortedMap.keySet()).containsExactly("a", "b", "c");

    var sortedNestedMap = (Map<String, Object>) sortedMap.get("b");
    assertThat(sortedNestedMap.keySet()).containsExactly("y", "z");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sample-sts-volumeclaimtemplates-desired.yaml",
        "sample-sts-volumeclaimtemplates-desired-with-status.yaml",
        "sample-sts-volumeclaimtemplates-desired-with-volumemode.yaml"
      })
  void testSanitizeState_statefulSetWithVolumeClaims(String desiredResourceFileName) {
    var desiredStatefulSet = loadResource(desiredResourceFileName, StatefulSet.class);
    var actualStatefulSet = loadResource("sample-sts-volumeclaimtemplates.yaml", StatefulSet.class);

    assertThat(matcher.matches(actualStatefulSet, desiredStatefulSet, mockedContext)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "sample-sts-volumeclaimtemplates-desired-add.yaml",
        "sample-sts-volumeclaimtemplates-desired-update.yaml",
        "sample-sts-volumeclaimtemplates-desired-with-status-mismatch.yaml",
        "sample-sts-volumeclaimtemplates-desired-with-volumemode-mismatch.yaml"
      })
  void testSanitizeState_statefulSetWithVolumeClaims_withMismatch(String desiredResourceFileName) {
    var desiredStatefulSet = loadResource(desiredResourceFileName, StatefulSet.class);
    var actualStatefulSet = loadResource("sample-sts-volumeclaimtemplates.yaml", StatefulSet.class);

    assertThat(matcher.matches(actualStatefulSet, desiredStatefulSet, mockedContext)).isFalse();
  }

  @Test
  void testSanitizeState_statefulSetWithResources() {
    var desiredStatefulSet = loadResource("sample-sts-resources-desired.yaml", StatefulSet.class);
    var actualStatefulSet = loadResource("sample-sts-resources.yaml", StatefulSet.class);

    assertThat(matcher.matches(actualStatefulSet, desiredStatefulSet, mockedContext)).isTrue();
  }

  @Test
  void testSanitizeState_statefulSetWithResources_withMismatch() {
    var desiredStatefulSet =
        loadResource("sample-sts-resources-desired-update.yaml", StatefulSet.class);
    var actualStatefulSet = loadResource("sample-sts-resources.yaml", StatefulSet.class);

    assertThat(matcher.matches(actualStatefulSet, desiredStatefulSet, mockedContext)).isFalse();
  }

  @Test
  void testSanitizeState_replicaSetWithResources() {
    var desiredReplicaSet = loadResource("sample-rs-resources-desired.yaml", ReplicaSet.class);
    var actualReplicaSet = loadResource("sample-rs-resources.yaml", ReplicaSet.class);

    assertThat(matcher.matches(actualReplicaSet, desiredReplicaSet, mockedContext)).isTrue();
  }

  @Test
  void testSanitizeState_replicaSetWithResources_withMismatch() {
    var desiredReplicaSet =
        loadResource("sample-rs-resources-desired-update.yaml", ReplicaSet.class);
    var actualReplicaSet = loadResource("sample-rs-resources.yaml", ReplicaSet.class);

    assertThat(matcher.matches(actualReplicaSet, desiredReplicaSet, mockedContext)).isFalse();
  }

  @Test
  void testSanitizeState_daemonSetWithResources() {
    var desiredDaemonSet = loadResource("sample-ds-resources-desired.yaml", DaemonSet.class);
    var actualDaemonSet = loadResource("sample-ds-resources.yaml", DaemonSet.class);

    assertThat(matcher.matches(actualDaemonSet, desiredDaemonSet, mockedContext)).isTrue();
  }

  @Test
  void testSanitizeState_daemonSetWithResources_withMismatch() {
    var desiredDaemonSet = loadResource("sample-ds-resources-desired-update.yaml", DaemonSet.class);
    var actualDaemonSet = loadResource("sample-ds-resources.yaml", DaemonSet.class);

    assertThat(matcher.matches(actualDaemonSet, desiredDaemonSet, mockedContext)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testCustomMatcher_returnsExpectedMatchBasedOnReadOnlyLabel(boolean readOnly) {
    var desiredConfigMap =
        loadResource("configmap.empty-owner-reference-desired.yaml", ConfigMap.class);
    desiredConfigMap.getData().put("key1", "another value");
    var actualConfigMap = loadResource("configmap.empty-owner-reference.yaml", ConfigMap.class);
    actualConfigMap.getMetadata().getLabels().put("readonly", Boolean.toString(readOnly));

    var matcher = new ReadOnlyAwareMatcher<ConfigMap>();
    assertThat(matcher.matches(actualConfigMap, desiredConfigMap, mockedContext))
        .isEqualTo(readOnly);
  }

  private static class ReadOnlyAwareMatcher<T extends HasMetadata>
      extends SSABasedGenericKubernetesResourceMatcher<T> {
    @Override
    protected boolean matches(
        Map<String, Object> actualMap,
        Map<String, Object> desiredMap,
        T actual,
        T desired,
        Context<?> context) {
      var readonly = actual.getMetadata().getLabels().get("readonly");
      if (readonly != null && readonly.equals("true")) {
        return true;
      }
      return actualMap.equals(desiredMap);
    }
  }

  private static <R> R loadResource(String fileName, Class<R> clazz) {
    return ReconcilerUtils.loadYaml(
        clazz, SSABasedGenericKubernetesResourceMatcherTest.class, fileName);
  }
}
