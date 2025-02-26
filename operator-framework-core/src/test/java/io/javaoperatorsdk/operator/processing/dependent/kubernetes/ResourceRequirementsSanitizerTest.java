package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Map;

import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.MockKubernetesClient;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.ResourceRequirementsSanitizer.sanitizeResourceRequirements;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the {@link ResourceRequirementsSanitizer} with combinations of matching and mismatching K8s
 * resources, using a mix of containers and init containers, as well as resource requests and
 * limits.
 */
class ResourceRequirementsSanitizerTest {

  private final Map<String, Object> actualMap = mock();

  private final KubernetesClient client = MockKubernetesClient.client(HasMetadata.class);
  private final KubernetesSerialization serialization = client.getKubernetesSerialization();

  @Test
  void testSanitizeResourceRequirements_whenTemplateIsNull_doNothing() {
    final var template = new PodTemplateSpecBuilder().build();

    sanitizeResourceRequirements(actualMap, null, template);
    sanitizeResourceRequirements(actualMap, template, null);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenTemplateSpecIsNull_doNothing() {
    final var template = new PodTemplateSpecBuilder().withSpec(null).build();
    final var templateWithSpec =
        new PodTemplateSpecBuilder().withNewSpec().endSpec().build();

    sanitizeResourceRequirements(actualMap, template, templateWithSpec);
    sanitizeResourceRequirements(actualMap, templateWithSpec, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenContainerSizeMismatch_doNothing() {
    final var template = new PodTemplateSpecBuilder()
        .withNewSpec()
        .addNewContainer()
        .withName("test")
        .endContainer()
        .endSpec()
        .build();
    final var templateWithTwoContainers = new PodTemplateSpecBuilder()
        .withNewSpec()
        .addNewContainer()
        .withName("test")
        .endContainer()
        .addNewContainer()
        .withName("test-new")
        .endContainer()
        .endSpec()
        .build();

    sanitizeResourceRequirements(actualMap, template, templateWithTwoContainers);
    sanitizeResourceRequirements(actualMap, templateWithTwoContainers, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenContainerNameMismatch_doNothing() {
    final var template = new PodTemplateSpecBuilder()
        .withNewSpec()
        .addNewContainer()
        .withName("test")
        .endContainer()
        .endSpec()
        .build();
    final var templateWithNewContainerName = new PodTemplateSpecBuilder()
        .withNewSpec()
        .addNewContainer()
        .withName("test-new")
        .endContainer()
        .endSpec()
        .build();

    sanitizeResourceRequirements(actualMap, template, templateWithNewContainerName);
    sanitizeResourceRequirements(actualMap, templateWithNewContainerName, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenResourceIsNull_doNothing() {
    final var template = new PodTemplateSpecBuilder()
        .withNewSpec()
        .addNewContainer()
        .withName("test")
        .endContainer()
        .endSpec()
        .build();
    final var templateWithResource = new PodTemplateSpecBuilder()
        .withNewSpec()
        .addNewContainer()
        .withName("test")
        .withNewResources()
        .endResources()
        .endContainer()
        .endSpec()
        .build();

    sanitizeResourceRequirements(actualMap, template, templateWithResource);
    sanitizeResourceRequirements(actualMap, templateWithResource, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenResourceSizeMismatch_doNothing() {
    final var actualMap = sanitizeRequestsAndLimits(
        ContainerType.CONTAINER,
        Map.of("cpu", new Quantity("2")),
        Map.of(),
        Map.of("cpu", new Quantity("4")),
        Map.of("cpu", new Quantity("4"), "memory", new Quantity("4Gi")));
    assertContainerResources(actualMap, "requests").hasSize(1).containsEntry("cpu", "2");
    assertContainerResources(actualMap, "limits").hasSize(1).containsEntry("cpu", "4");
  }

  @Test
  void testSanitizeResourceRequirements_whenResourceKeyMismatch_doNothing() {
    final var actualMap = sanitizeRequestsAndLimits(
        ContainerType.INIT_CONTAINER,
        Map.of("cpu", new Quantity("2")),
        Map.of("memory", new Quantity("4Gi")),
        Map.of(),
        Map.of());
    assertInitContainerResources(actualMap, "requests").hasSize(1).containsEntry("cpu", "2");
    assertInitContainerResources(actualMap, "limits").isNull();
  }

  @Test
  void testSanitizeResourceRequirements_whenResourcesHaveSameAmountAndFormat_doNothing() {
    final var actualMap = sanitizeRequestsAndLimits(
        ContainerType.CONTAINER,
        Map.of("memory", new Quantity("4Gi")),
        Map.of("memory", new Quantity("4Gi")),
        Map.of("cpu", new Quantity("2")),
        Map.of("cpu", new Quantity("2")));
    assertContainerResources(actualMap, "requests").hasSize(1).containsEntry("memory", "4Gi");
    assertContainerResources(actualMap, "limits").hasSize(1).containsEntry("cpu", "2");
  }

  @Test
  void testSanitizeResourceRequirements_whenResourcesHaveNumericalAmountMismatch_doNothing() {
    final var actualMap = sanitizeRequestsAndLimits(
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
      testSanitizeResourceRequirements_whenResourcesHaveAmountAndFormatMismatchWithSameNumericalAmount_thenSanitizeActualMap() {
    final var actualMap = sanitizeRequestsAndLimits(
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> sanitizeRequestsAndLimits(
      final ContainerType type,
      final Map<String, Quantity> actualRequests,
      final Map<String, Quantity> desiredRequests,
      final Map<String, Quantity> actualLimits,
      final Map<String, Quantity> desiredLimits) {
    final var actual = createStatefulSet(type, actualRequests, actualLimits);
    final var desired = createStatefulSet(type, desiredRequests, desiredLimits);
    final var actualMap = serialization.convertValue(actual, Map.class);
    sanitizeResourceRequirements(
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
      final Map<String, Quantity> limits) {
    var builder = new StatefulSetBuilder().withNewSpec().withNewTemplate().withNewSpec();
    if (type == ContainerType.CONTAINER) {
      builder = builder
          .addNewContainer()
          .withName("test")
          .withNewResources()
          .withRequests(requests)
          .withLimits(limits)
          .endResources()
          .endContainer();
    } else {
      builder = builder
          .addNewInitContainer()
          .withName("test")
          .withNewResources()
          .withRequests(requests)
          .withLimits(limits)
          .endResources()
          .endInitContainer();
    }
    return builder.endSpec().endTemplate().endSpec().build();
  }

  private static MapAssert<String, Object> assertContainerResources(
      final Map<String, Object> actualMap, final String resourceName) {
    return assertThat(GenericKubernetesResource.<Map<String, Object>>get(
        actualMap, "spec", "template", "spec", "containers", 0, "resources", resourceName));
  }

  private static MapAssert<String, Object> assertInitContainerResources(
      final Map<String, Object> actualMap, final String resourceName) {
    return assertThat(GenericKubernetesResource.<Map<String, Object>>get(
        actualMap, "spec", "template", "spec", "initContainers", 0, "resources", resourceName));
  }
}
