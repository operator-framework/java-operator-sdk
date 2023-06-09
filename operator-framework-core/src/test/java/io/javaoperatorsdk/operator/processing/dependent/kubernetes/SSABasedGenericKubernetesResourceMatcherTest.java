package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
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
    when(controllerConfiguration.fieldManager()).thenCallRealMethod();
    var configurationService = mock(ConfigurationService.class);
    when(configurationService.getObjectMapper()).thenCallRealMethod();
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

}
