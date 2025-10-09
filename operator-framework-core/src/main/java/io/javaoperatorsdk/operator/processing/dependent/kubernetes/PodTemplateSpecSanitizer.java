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
package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;

/**
 * Sanitizes the {@link ResourceRequirements} and the {@link EnvVar} in the containers of a pair of
 * {@link PodTemplateSpec} instances.
 *
 * <p>When the sanitizer finds a mismatch in the structure of the given templates, before it gets to
 * the nested fields, it returns early without fixing the actual map. This is an optimization
 * because the given templates will anyway differ at this point. This means we do not have to
 * attempt to sanitize the fields for these use cases, since there will anyway be an update of the
 * K8s resource.
 *
 * <p>The algorithm traverses the whole template structure because we need the actual and desired
 * {@link Quantity} and {@link EnvVar} instances. Using the {@link
 * GenericKubernetesResource#get(Map, Object...)} shortcut would need to create new instances just
 * for the sanitization check.
 */
class PodTemplateSpecSanitizer {

  static void sanitizePodTemplateSpec(
      final Map<String, Object> actualMap,
      final PodTemplateSpec actualTemplate,
      final PodTemplateSpec desiredTemplate) {
    if (actualTemplate == null || desiredTemplate == null) {
      return;
    }
    if (actualTemplate.getSpec() == null || desiredTemplate.getSpec() == null) {
      return;
    }
    sanitizePodTemplateSpec(
        actualMap,
        actualTemplate.getSpec().getInitContainers(),
        desiredTemplate.getSpec().getInitContainers(),
        "initContainers");
    sanitizePodTemplateSpec(
        actualMap,
        actualTemplate.getSpec().getContainers(),
        desiredTemplate.getSpec().getContainers(),
        "containers");
  }

  private static void sanitizePodTemplateSpec(
      final Map<String, Object> actualMap,
      final List<Container> actualContainers,
      final List<Container> desiredContainers,
      final String containerPath) {
    int containers = desiredContainers.size();
    if (containers == actualContainers.size()) {
      for (int containerIndex = 0; containerIndex < containers; containerIndex++) {
        final var desiredContainer = desiredContainers.get(containerIndex);
        final var actualContainer = actualContainers.get(containerIndex);
        if (!desiredContainer.getName().equals(actualContainer.getName())) {
          return;
        }
        sanitizeEnvVars(
            actualMap,
            actualContainer.getEnv(),
            desiredContainer.getEnv(),
            containerPath,
            containerIndex);
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
    Optional.ofNullable(
            GenericKubernetesResource.get(
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
        .ifPresent(
            m ->
                actualResource.forEach(
                    (key, actualQuantity) -> {
                      final var desiredQuantity = desiredResource.get(key);
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
                        // replace the actual Quantity with the desired Quantity to prevent a
                        // resource update
                        m.replace(key, desiredQuantity.toString());
                      }
                    }));
  }

  @SuppressWarnings("unchecked")
  private static void sanitizeEnvVars(
      final Map<String, Object> actualMap,
      final List<EnvVar> actualEnvVars,
      final List<EnvVar> desiredEnvVars,
      final String containerPath,
      final int containerIndex) {
    if (desiredEnvVars.isEmpty() || actualEnvVars.isEmpty()) {
      return;
    }
    Optional.ofNullable(
            GenericKubernetesResource.get(
                actualMap, "spec", "template", "spec", containerPath, containerIndex, "env"))
        .map(List.class::cast)
        .ifPresent(
            envVars ->
                actualEnvVars.forEach(
                    actualEnvVar -> {
                      final var actualEnvVarName = actualEnvVar.getName();
                      final var actualEnvVarValue = actualEnvVar.getValue();
                      // check if the actual EnvVar value string is not null or the desired EnvVar
                      // already contains the same EnvVar name with a non empty EnvVar value
                      final var isDesiredEnvVarEmpty =
                          hasEnvVarNoEmptyValue(actualEnvVarName, desiredEnvVars);
                      if (actualEnvVarValue != null || isDesiredEnvVarEmpty) {
                        return;
                      }
                      envVars.stream()
                          .filter(
                              envVar ->
                                  ((Map<String, String>) envVar)
                                      .get("name")
                                      .equals(actualEnvVarName))
                          // add the actual EnvVar value with an empty string to prevent a
                          // resource update
                          .forEach(envVar -> ((Map<String, String>) envVar).put("value", ""));
                    }));
  }

  private static boolean hasEnvVarNoEmptyValue(
      final String envVarName, final List<EnvVar> envVars) {
    return envVars.stream()
        .anyMatch(
            envVar ->
                Objects.equals(envVarName, envVar.getName())
                    && envVar.getValue() != null
                    && !envVar.getValue().isEmpty());
  }
}
