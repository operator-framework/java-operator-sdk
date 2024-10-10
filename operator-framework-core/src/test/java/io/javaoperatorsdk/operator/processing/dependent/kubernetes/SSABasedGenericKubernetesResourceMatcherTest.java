package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SSABasedGenericKubernetesResourceMatcherTest {

  Context<?> mockedContext = mock(Context.class);

  SSABasedGenericKubernetesResourceMatcher<HasMetadata> matcher =
      new SSABasedGenericKubernetesResourceMatcher<>();

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    var controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.fieldManager()).thenReturn("controller");
    var configurationService = mock(ConfigurationService.class);

    final var client = MockKubernetesClient.client(HasMetadata.class);
    when(mockedContext.getClient()).thenReturn(client);

    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
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

  private <R> R loadResource(String fileName, Class<R> clazz) {
    return ReconcilerUtils.loadYaml(clazz, SSABasedGenericKubernetesResourceMatcherTest.class,
        fileName);
  }

  @Test
  @SuppressWarnings("unchecked")
  void sortListItemsTest() {
    Map<String, Object> nestedMap1 = new HashMap<>();
    nestedMap1.put("z", 26);
    nestedMap1.put("y", 25);

    Map<String, Object> nestedMap2 = new HashMap<>();
    nestedMap2.put("b", 26);
    nestedMap2.put("c", 25);
    nestedMap2.put("a", 24);

    List<Object> unsortedListItems = Arrays.asList(1, nestedMap1, nestedMap2);
    List<Object> sortedListItems = matcher.sortListItems(unsortedListItems);

    assertThat(sortedListItems).element(0).isEqualTo(1);

    Map<String, Object> sortedNestedMap1 = (Map<String, Object>) sortedListItems.get(1);
    assertThat(sortedNestedMap1.keySet()).containsExactly("y", "z");

    Map<String, Object> sortedNestedMap2 = (Map<String, Object>) sortedListItems.get(2);
    assertThat(sortedNestedMap2.keySet()).containsExactly("a", "b", "c");
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSortMapWithNestedMap() {
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("z", 26);
    nestedMap.put("y", 25);

    Map<String, Object> unsortedMap = new HashMap<>();
    unsortedMap.put("b", nestedMap);
    unsortedMap.put("a", 1);
    unsortedMap.put("c", 2);

    Map<String, Object> sortedMap = matcher.sortMap(unsortedMap);

    assertThat(sortedMap.keySet()).containsExactly("a", "b", "c");

    Map<String, Object> sortedNestedMap = (Map<String, Object>) sortedMap.get("b");
    assertThat(sortedNestedMap.keySet()).containsExactly("y", "z");
  }
}
