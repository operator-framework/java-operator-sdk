package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.ResourceRequirementsSanitizer.sanitizeResourceRequirements;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
    final var templateWithSpec = new PodTemplateSpecBuilder().withNewSpec().endSpec().build();

    sanitizeResourceRequirements(actualMap, template, templateWithSpec);
    sanitizeResourceRequirements(actualMap, templateWithSpec, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenContainerSizeMismatch_doNothing() {
    final var template = new PodTemplateSpecBuilder().withNewSpec()
        .addNewContainer().withName("test").endContainer()
        .endSpec().build();
    final var templateWithTwoContainers = new PodTemplateSpecBuilder().withNewSpec()
        .addNewContainer().withName("test").endContainer()
        .addNewContainer().withName("test-new").endContainer()
        .endSpec().build();

    sanitizeResourceRequirements(actualMap, template, templateWithTwoContainers);
    sanitizeResourceRequirements(actualMap, templateWithTwoContainers, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenContainerNameMismatch_doNothing() {
    final var template = new PodTemplateSpecBuilder().withNewSpec()
        .addNewContainer().withName("test").endContainer()
        .endSpec().build();
    final var templateWithNewContainerName = new PodTemplateSpecBuilder().withNewSpec()
        .addNewContainer().withName("test-new").endContainer()
        .endSpec().build();

    sanitizeResourceRequirements(actualMap, template, templateWithNewContainerName);
    sanitizeResourceRequirements(actualMap, templateWithNewContainerName, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenResourceIsNull_doNothing() {
    final var template = new PodTemplateSpecBuilder().withNewSpec()
        .addNewContainer().withName("test").endContainer()
        .endSpec().build();
    final var templateWithResource = new PodTemplateSpecBuilder().withNewSpec()
        .addNewContainer().withName("test").withNewResources().endResources().endContainer()
        .endSpec().build();

    sanitizeResourceRequirements(actualMap, template, templateWithResource);
    sanitizeResourceRequirements(actualMap, templateWithResource, template);
    verifyNoInteractions(actualMap);
  }

  @Test
  void testSanitizeResourceRequirements_whenResourceSizeMismatch_doNothing() {
    final var actualMap = sanitizeRequestsAndLimits(
        Map.of("cpu", new Quantity("2")),
        Map.of(),
        Map.of("cpu", new Quantity("4")),
        Map.of("cpu", new Quantity("4"), "memory", new Quantity("4Gi")));
    assertResources(actualMap, "requests")
        .hasSize(1)
        .containsEntry("cpu", "2");
    assertResources(actualMap, "limits")
        .hasSize(1)
        .containsEntry("cpu", "4");
  }

  @Test
  void testSanitizeResourceRequirements_whenResourceKeyMismatch_doNothing() {
    final var actualMap = sanitizeRequestsAndLimits(
        Map.of("cpu", new Quantity("2")),
        Map.of("memory", new Quantity("4Gi")),
        Map.of(),
        Map.of());
    assertResources(actualMap, "requests")
        .hasSize(1)
        .containsEntry("cpu", "2");
    assertResources(actualMap, "limits").isNull();
  }

  @Test
  void testSanitizeResourceRequirements_whenResourcesHaveSameAmountAndFormat_doNothing() {
    final var actualMap = sanitizeRequestsAndLimits(
        Map.of("memory", new Quantity("4Gi")),
        Map.of("memory", new Quantity("4Gi")),
        Map.of("cpu", new Quantity("2")),
        Map.of("cpu", new Quantity("2")));
    assertResources(actualMap, "requests")
        .hasSize(1)
        .containsEntry("memory", "4Gi");
    assertResources(actualMap, "limits")
        .hasSize(1)
        .containsEntry("cpu", "2");
  }

  @Test
  void testSanitizeResourceRequirements_whenResourcesHaveNumericalAmountMismatch_doNothing() {
    final var actualMap = sanitizeRequestsAndLimits(
        Map.of("cpu", new Quantity("2"), "memory", new Quantity("4Gi")),
        Map.of("cpu", new Quantity("4"), "memory", new Quantity("4Ti")),
        Map.of("cpu", new Quantity("2")),
        Map.of("cpu", new Quantity("4000m")));
    assertResources(actualMap, "requests")
        .hasSize(2)
        .containsEntry("cpu", "2")
        .containsEntry("memory", "4Gi");
    assertResources(actualMap, "limits")
        .hasSize(1)
        .containsEntry("cpu", "2");
  }

  @Test
  void testSanitizeResourceRequirements_whenResourcesHaveAmountAndFormatMismatchWithSameNumericalAmount_thenSanitizeActualMap() {
    final var actualMap = sanitizeRequestsAndLimits(
        Map.of("cpu", new Quantity("2"), "memory", new Quantity("4Gi")),
        Map.of("cpu", new Quantity("2000m"), "memory", new Quantity("4096Mi")),
        Map.of("cpu", new Quantity("4")),
        Map.of("cpu", new Quantity("4000m")));
    assertResources(actualMap, "requests")
        .hasSize(2)
        .containsEntry("cpu", "2000m")
        .containsEntry("memory", "4096Mi");
    assertResources(actualMap, "limits")
        .hasSize(1)
        .containsEntry("cpu", "4000m");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> sanitizeRequestsAndLimits(
      final Map<String, Quantity> actualRequests, final Map<String, Quantity> desiredRequests,
      final Map<String, Quantity> actualLimits, final Map<String, Quantity> desiredLimits) {
    final var actual = new StatefulSetBuilder().withNewSpec().withNewTemplate().withNewSpec()
        .addNewContainer()
        .withName("test")
        .withNewResources()
        .withRequests(actualRequests).withLimits(actualLimits)
        .endResources()
        .endContainer()
        .endSpec().endTemplate().endSpec().build();
    final var desired = new StatefulSetBuilder().withNewSpec().withNewTemplate().withNewSpec()
        .addNewContainer()
        .withName("test")
        .withNewResources()
        .withRequests(desiredRequests).withLimits(desiredLimits)
        .endResources()
        .endContainer()
        .endSpec().endTemplate().endSpec().build();

    final var actualMap = serialization.convertValue(actual, Map.class);
    sanitizeResourceRequirements(actualMap,
        actual.getSpec().getTemplate(),
        desired.getSpec().getTemplate());
    return actualMap;
  }

  private static MapAssert<String, Object> assertResources(final Map<String, Object> actualMap,
      final String resourceName) {
    return assertThat(GenericKubernetesResource.<Map<String, Object>>get(actualMap,
        "spec", "template", "spec", "containers", 0, "resources", resourceName));
  }
}
