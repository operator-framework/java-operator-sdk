package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;

/**
 * Sanitizes the {@link ResourceRequirements} in the containers of a pair of {@link PodTemplateSpec}
 * instances.
 * <p>
 * When the sanitizer finds a mismatch in the structure of the given templates, before it gets to
 * the nested resource limits and requests, it returns early without fixing the actual map. This is
 * an optimization because the given templates will anyway differ at this point. This means we do
 * not have to attempt to sanitize the resources for these use cases, since there will anyway be an
 * update of the K8s resource.
 * <p>
 * The algorithm traverses the whole template structure because we need the actual and desired
 * {@link Quantity} instances to compare their numerical amount. Using the
 * {@link GenericKubernetesResource#get(Map, Object...)} shortcut would need to create new instances
 * just for the sanitization check.
 */
class ResourceRequirementsSanitizer {

  static void sanitizeResourceRequirements(
      final Map<String, Object> actualMap,
      final PodTemplateSpec actualTemplate,
      final PodTemplateSpec desiredTemplate) {
    if (actualTemplate == null || desiredTemplate == null) {
      return;
    }
    if (actualTemplate.getSpec() == null || desiredTemplate.getSpec() == null) {
      return;
    }
    sanitizeResourceRequirements(
        actualMap,
        actualTemplate.getSpec().getInitContainers(),
        desiredTemplate.getSpec().getInitContainers(),
        "initContainers");
    sanitizeResourceRequirements(
        actualMap,
        actualTemplate.getSpec().getContainers(),
        desiredTemplate.getSpec().getContainers(),
        "containers");
  }

  private static void sanitizeResourceRequirements(
      final Map<String, Object> actualMap,
      final List<Container> actualContainers,
      final List<Container> desiredContainers,
      final String containerPath) {
    int containers = desiredContainers.size();
    if (containers == actualContainers.size()) {
      for (int containerIndex = 0; containerIndex < containers; containerIndex++) {
        var desiredContainer = desiredContainers.get(containerIndex);
        var actualContainer = actualContainers.get(containerIndex);
        if (!desiredContainer.getName().equals(actualContainer.getName())) {
          return;
        }
        sanitizeResourceRequirements(
            actualMap,
            actualContainer.getResources(),
            desiredContainer.getResources(),
            containerPath,
            containerIndex);
      }
    }
  }

  private static void sanitizeResourceRequirements(
      final Map<String, Object> actualMap,
      final ResourceRequirements actualResource,
      final ResourceRequirements desiredResource,
      final String containerPath,
      final int containerIndex) {
    if (desiredResource == null || actualResource == null) {
      return;
    }
    sanitizeQuantities(
        actualMap,
        actualResource.getRequests(),
        desiredResource.getRequests(),
        containerPath,
        containerIndex,
        "requests");
    sanitizeQuantities(
        actualMap,
        actualResource.getLimits(),
        desiredResource.getLimits(),
        containerPath,
        containerIndex,
        "limits");
  }

  @SuppressWarnings("unchecked")
  private static void sanitizeQuantities(
      final Map<String, Object> actualMap,
      final Map<String, Quantity> actualResource,
      final Map<String, Quantity> desiredResource,
      final String containerPath,
      final int containerIndex,
      final String quantityPath) {
    Optional.ofNullable(GenericKubernetesResource.get(
            actualMap,
            "spec",
            "template",
            "spec",
            containerPath,
            containerIndex,
            "resources",
            quantityPath))
        .map(Map.class::cast)
        .filter(m -> m.size() == desiredResource.size())
        .ifPresent(m -> actualResource.forEach((key, actualQuantity) -> {
          var desiredQuantity = desiredResource.get(key);
          if (desiredQuantity == null) {
            return;
          }
          // check if the string representation of the Quantity instances is equal
          if (actualQuantity.getAmount().equals(desiredQuantity.getAmount())
              && actualQuantity.getFormat().equals(desiredQuantity.getFormat())) {
            return;
          }
          // check if the numerical amount of the Quantity instances is equal
          if (actualQuantity.equals(desiredQuantity)) {
            // replace the actual Quantity with the desired Quantity to prevent a resource update
            m.replace(key, desiredQuantity.toString());
          }
        }));
  }
}
