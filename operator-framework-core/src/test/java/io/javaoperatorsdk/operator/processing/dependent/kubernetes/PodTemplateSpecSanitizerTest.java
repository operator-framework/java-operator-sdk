package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.List;
import java.util.Map;

import org.assertj.core.api.ListAssert;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.MockKubernetesClient;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.PodTemplateSpecSanitizer.sanitizePodTemplateSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the {@link PodTemplateSpecSanitizer} with combinations of matching and mismatching K8s
 * resources, using a mix of containers and init containers, as well as resource requests and limits
 * along with environment variables.
 */
class PodTemplateSpecSanitizerTest {

  private final Map<String, Object> actualMap = mock();

  private final KubernetesClient client = MockKubernetesClient.client(HasMetadata.class);
  private final KubernetesSerialization serialization = client.getKubernetesSerialization();

  @Test
  void testSanitizePodTemplateSpec_whenTemplateIsNull_doNothing() {
    final var template = new PodTemplateSpecBuilder().build();

    sanitizePodTemplateSpec(actualMap, null, template);
    sanitizePodTemplateSpec(actualMap, template, null);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizePodTemplateSpec_whenTemplateSpecIsNull_doNothing() {
    final var template = new PodTemplateSpecBuilder().withSpec(null).build();
    final var templateWithSpec = new PodTemplateSpecBuilder().withNewSpec().endSpec().build();

    sanitizePodTemplateSpec(actualMap, template, templateWithSpec);
    sanitizePodTemplateSpec(actualMap, templateWithSpec, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizePodTemplateSpec_whenContainerSizeMismatch_doNothing() {
    final var template =
        new PodTemplateSpecBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("test")
            .endContainer()
            .endSpec()
            .build();
    final var templateWithTwoContainers =
        new PodTemplateSpecBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("test")
            .endContainer()
            .addNewContainer()
            .withName("test-new")
            .endContainer()
            .endSpec()
            .build();

    sanitizePodTemplateSpec(actualMap, template, templateWithTwoContainers);
    sanitizePodTemplateSpec(actualMap, templateWithTwoContainers, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizePodTemplateSpec_whenContainerNameMismatch_doNothing() {
    final var template =
        new PodTemplateSpecBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("test")
            .endContainer()
            .endSpec()
            .build();
    final var templateWithNewContainerName =
        new PodTemplateSpecBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("test-new")
            .endContainer()
            .endSpec()
            .build();

    sanitizePodTemplateSpec(actualMap, template, templateWithNewContainerName);
    sanitizePodTemplateSpec(actualMap, templateWithNewContainerName, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizePodTemplateSpec_whenResourceIsNull_doNothing() {
    final var template =
        new PodTemplateSpecBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("test")
            .endContainer()
            .endSpec()
            .build();
    final var templateWithResource =
        new PodTemplateSpecBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("test")
            .withNewResources()
            .endResources()
            .endContainer()
            .endSpec()
            .build();

    sanitizePodTemplateSpec(actualMap, template, templateWithResource);
    sanitizePodTemplateSpec(actualMap, templateWithResource, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenResourceKeyMismatch_doNothing() {
    final var actualMap =
        sanitizeRequestsAndLimits(
            ContainerType.INIT_CONTAINER,
            Map.of("cpu", new Quantity("2")),
            Map.of("memory", new Quantity("4Gi")),
            Map.of(),
            Map.of());
    assertInitContainerResources(actualMap, "requests").hasSize(1).containsEntry("cpu", "2");
    assertInitContainerResources(actualMap, "limits").isNull();
  }

  @Test
  void testSanitizePodTemplateSpec_whenResourcesHaveSameAmountAndFormat_doNothing() {
    final var actualMap =
        sanitizeRequestsAndLimits(
            ContainerType.CONTAINER,
            Map.of("memory", new Quantity("4Gi")),
            Map.of("memory", new Quantity("4Gi")),
            Map.of("cpu", new Quantity("2")),
            Map.of("cpu", new Quantity("2")));
    assertContainerResources(actualMap, "requests").hasSize(1).containsEntry("memory", "4Gi");
    assertContainerResources(actualMap, "limits").hasSize(1).containsEntry("cpu", "2");
  }

  @Test
  void testSanitizePodTemplateSpec_whenResourcesHaveNumericalAmountMismatch_doNothing() {
    final var actualMap =
        sanitizeRequestsAndLimits(
            ContainerType.INIT_CONTAINER,
            Map.of("cpu", new Quantity("2"), "memory", new Quantity("4Gi")),
            Map.of("cpu", new Quantity("4"), "memory", new Quantity("4Ti")),
            Map.of("cpu", new Quantity("2")),
            Map.of("cpu", new Quantity("4000m")));
    assertInitContainerResources(actualMap, "requests")
        .hasSize(2)
        .containsEntry("cpu", "2")
        .containsEntry("memory", "4Gi");
    assertInitContainerResources(actualMap, "limits").hasSize(1).containsEntry("cpu", "2");
  }

  @Test
  void
      testSanitizePodTemplateSpec_whenResourcesHaveNumericalAmountMismatch_withEphemeralStorageAddedByOtherOperator_doNothing() {
    // mimics an environment like GKE Autopilot that enforces ephemeral-storage requests and limits
    final var actualMap =
        sanitizeRequestsAndLimits(
            ContainerType.INIT_CONTAINER,
            Map.of(
                "cpu",
                new Quantity("2"),
                "memory",
                new Quantity("4Gi"),
                "ephemeral-storage",
                new Quantity("1Gi")),
            Map.of("cpu", new Quantity("4"), "memory", new Quantity("4Ti")),
            Map.of("cpu", new Quantity("2"), "ephemeral-storage", new Quantity("1Gi")),
            Map.of("cpu", new Quantity("4000m")));
    assertInitContainerResources(actualMap, "requests")
        .hasSize(3)
        .containsEntry("cpu", "2")
        .containsEntry("memory", "4Gi")
        .containsEntry("ephemeral-storage", "1Gi");
    assertInitContainerResources(actualMap, "limits")
        .hasSize(2)
        .containsEntry("cpu", "2")
        .containsEntry("ephemeral-storage", "1Gi");
  }

  @Test
  void
      testSanitizeResourceRequirements_whenResourcesHaveAmountAndFormatMismatchWithSameNumericalAmount_thenSanitizeActualMap() {
    final var actualMap =
        sanitizeRequestsAndLimits(
            ContainerType.CONTAINER,
            Map.of("cpu", new Quantity("2"), "memory", new Quantity("4Gi")),
            Map.of("cpu", new Quantity("2000m"), "memory", new Quantity("4096Mi")),
            Map.of("cpu", new Quantity("4")),
            Map.of("cpu", new Quantity("4000m")));
    assertContainerResources(actualMap, "requests")
        .hasSize(2)
        .containsEntry("cpu", "2000m")
        .containsEntry("memory", "4096Mi");
    assertContainerResources(actualMap, "limits").hasSize(1).containsEntry("cpu", "4000m");
  }

  @Test
  void
      testSanitizeResourceRequirements_whenResourcesHaveAmountAndFormatMismatchWithSameNumericalAmount_withEphemeralStorageAddedByOtherOperator_thenSanitizeActualMap() {
    // mimics an environment like GKE Autopilot that enforces ephemeral-storage requests and limits
    final var actualMap =
        sanitizeRequestsAndLimits(
            ContainerType.CONTAINER,
            Map.of(
                "cpu",
                new Quantity("2"),
                "memory",
                new Quantity("4Gi"),
                "ephemeral-storage",
                new Quantity("1Gi")),
            Map.of("cpu", new Quantity("2000m"), "memory", new Quantity("4096Mi")),
            Map.of("cpu", new Quantity("4"), "ephemeral-storage", new Quantity("1Gi")),
            Map.of("cpu", new Quantity("4000m")));
    assertContainerResources(actualMap, "requests")
        .hasSize(3)
        .containsEntry("cpu", "2000m")
        .containsEntry("memory", "4096Mi")
        .containsEntry("ephemeral-storage", "1Gi");
    assertContainerResources(actualMap, "limits")
        .hasSize(2)
        .containsEntry("cpu", "4000m")
        .containsEntry("ephemeral-storage", "1Gi");
  }

  @Test
  void testSanitizePodTemplateSpec_whenEnvVarsIsEmpty_doNothing() {
    final var template =
        new PodTemplateSpecBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("test")
            .endContainer()
            .endSpec()
            .build();
    final var templateWithEnvVars =
        new PodTemplateSpecBuilder()
            .withNewSpec()
            .addNewContainer()
            .withName("test")
            .withEnv(List.of(new EnvVarBuilder().withName("FOO").withValue("foobar").build()))
            .endContainer()
            .endSpec()
            .build();

    sanitizePodTemplateSpec(actualMap, template, templateWithEnvVars);
    sanitizePodTemplateSpec(actualMap, templateWithEnvVars, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizePodTemplateSpec_whenActualEnvVarValueIsNotEmpty_doNothing() {
    final var actualMap =
        sanitizeEnvVars(
            ContainerType.CONTAINER,
            List.of(
                new EnvVarBuilder().withName("FOO").withValue("foo").build(),
                new EnvVarBuilder().withName("BAR").withValue("bar").build()),
            List.of(
                new EnvVarBuilder().withName("FOO").withValue("bar").build(),
                new EnvVarBuilder().withName("BAR").withValue("foo").build()));
    assertContainerEnvVars(actualMap)
        .hasSize(2)
        .containsExactly(
            Map.of("name", "FOO", "value", "foo"), Map.of("name", "BAR", "value", "bar"));
  }

  @Test
  void testSanitizePodTemplateSpec_whenActualAndDesiredEnvVarsAreDifferent_doNothing() {
    final var actualMap =
        sanitizeEnvVars(
            ContainerType.INIT_CONTAINER,
            List.of(new EnvVarBuilder().withName("FOO").withValue("foo").build()),
            List.of(new EnvVarBuilder().withName("BAR").withValue("bar").build()));
    assertInitContainerEnvVars(actualMap)
        .hasSize(1)
        .containsExactly(Map.of("name", "FOO", "value", "foo"));
  }

  @Test
  void testSanitizePodTemplateSpec_whenActualEnvVarIsEmpty_doNothing() {
    final var actualMap =
        sanitizeEnvVars(
            ContainerType.INIT_CONTAINER,
            List.of(
                new EnvVarBuilder().withName("FOO").withValue("").build(),
                new EnvVarBuilder().withName("BAR").withValue("").build()),
            List.of(
                new EnvVarBuilder().withName("FOO").withValue("foo").build(),
                new EnvVarBuilder().withName("BAR").withValue("").build()));
    assertInitContainerEnvVars(actualMap)
        .hasSize(2)
        .containsExactly(Map.of("name", "FOO", "value", ""), Map.of("name", "BAR", "value", ""));
  }

  @Test
  void testSanitizePodTemplateSpec_whenActualEnvVarIsNull_doNothing() {
    final var actualMap =
        sanitizeEnvVars(
            ContainerType.CONTAINER,
            List.of(
                new EnvVarBuilder().withName("FOO").withValue(null).build(),
                new EnvVarBuilder().withName("BAR").withValue(null).build()),
            List.of(
                new EnvVarBuilder().withName("FOO").withValue("foo").build(),
                new EnvVarBuilder().withName("BAR").withValue(" ").build()));
    assertContainerEnvVars(actualMap)
        .hasSize(2)
        .containsExactly(Map.of("name", "FOO"), Map.of("name", "BAR"));
  }

  @Test
  void
      testSanitizePodTemplateSpec_whenActualEnvVarIsNull_withDesiredEnvVarEmpty_thenSanitizeActualMap() {
    final var actualMap =
        sanitizeEnvVars(
            ContainerType.CONTAINER,
            List.of(
                new EnvVarBuilder().withName("FOO").withValue(null).build(),
                new EnvVarBuilder().withName("BAR").withValue(null).build()),
            List.of(
                new EnvVarBuilder().withName("FOO").withValue("").build(),
                new EnvVarBuilder().withName("BAR").withValue("").build()));
    assertContainerEnvVars(actualMap)
        .hasSize(2)
        .containsExactly(Map.of("name", "FOO", "value", ""), Map.of("name", "BAR", "value", ""));
  }

  private Map<String, Object> sanitizeRequestsAndLimits(
      final ContainerType type,
      final Map<String, Quantity> actualRequests,
      final Map<String, Quantity> desiredRequests,
      final Map<String, Quantity> actualLimits,
      final Map<String, Quantity> desiredLimits) {
    return sanitize(
        type, actualRequests, desiredRequests, actualLimits, desiredLimits, List.of(), List.of());
  }

  private Map<String, Object> sanitizeEnvVars(
      final ContainerType type,
      final List<EnvVar> actualEnvVars,
      final List<EnvVar> desiredEnvVars) {
    return sanitize(type, Map.of(), Map.of(), Map.of(), Map.of(), actualEnvVars, desiredEnvVars);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> sanitize(
      final ContainerType type,
      final Map<String, Quantity> actualRequests,
      final Map<String, Quantity> desiredRequests,
      final Map<String, Quantity> actualLimits,
      final Map<String, Quantity> desiredLimits,
      final List<EnvVar> actualEnvVars,
      final List<EnvVar> desiredEnvVars) {
    final var actual = createStatefulSet(type, actualRequests, actualLimits, actualEnvVars);
    final var desired = createStatefulSet(type, desiredRequests, desiredLimits, desiredEnvVars);
    final var actualMap = serialization.convertValue(actual, Map.class);
    sanitizePodTemplateSpec(
        actualMap, actual.getSpec().getTemplate(), desired.getSpec().getTemplate());
    return actualMap;
  }

  private enum ContainerType {
    CONTAINER,
    INIT_CONTAINER,
  }

  private static StatefulSet createStatefulSet(
      final ContainerType type,
      final Map<String, Quantity> requests,
      final Map<String, Quantity> limits,
      final List<EnvVar> envVars) {
    var builder = new StatefulSetBuilder().withNewSpec().withNewTemplate().withNewSpec();
    if (type == ContainerType.CONTAINER) {
      builder =
          builder
              .addNewContainer()
              .withName("test")
              .withNewResources()
              .withRequests(requests)
              .withLimits(limits)
              .endResources()
              .withEnv(envVars)
              .endContainer();
    } else {
      builder =
          builder
              .addNewInitContainer()
              .withName("test")
              .withNewResources()
              .withRequests(requests)
              .withLimits(limits)
              .endResources()
              .withEnv(envVars)
              .endInitContainer();
    }
    return builder.endSpec().endTemplate().endSpec().build();
  }

  private static MapAssert<String, Object> assertContainerResources(
      final Map<String, Object> actualMap, final String resourceName) {
    return assertThat(
        GenericKubernetesResource.<Map<String, Object>>get(
            actualMap, "spec", "template", "spec", "containers", 0, "resources", resourceName));
  }

  private static MapAssert<String, Object> assertInitContainerResources(
      final Map<String, Object> actualMap, final String resourceName) {
    return assertThat(
        GenericKubernetesResource.<Map<String, Object>>get(
            actualMap, "spec", "template", "spec", "initContainers", 0, "resources", resourceName));
  }

  private static ListAssert<Map<String, Object>> assertContainerEnvVars(
      final Map<String, Object> actualMap) {
    return assertThat(
        GenericKubernetesResource.<List<Map<String, Object>>>get(
            actualMap, "spec", "template", "spec", "containers", 0, "env"));
  }

  private static ListAssert<Map<String, Object>> assertInitContainerEnvVars(
      final Map<String, Object> actualMap) {
    return assertThat(
        GenericKubernetesResource.<List<Map<String, Object>>>get(
            actualMap, "spec", "template", "spec", "initContainers", 0, "env"));
  }
}
