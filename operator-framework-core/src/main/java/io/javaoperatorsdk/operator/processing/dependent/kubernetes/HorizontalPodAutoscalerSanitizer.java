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
import java.util.Optional;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerSpec;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpec;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricTarget;

/**
 * Sanitizes the target quantities ({@code value} and {@code averageValue}) of the metrics in a pair
 * of {@link HorizontalPodAutoscalerSpec} instances.
 *
 * <p>When the number of metrics differs, the sanitizer returns early without fixing the actual map,
 * because the specs differ anyway. A metric source that is only present on one side, for example
 * because the metric type differs, is skipped for the same reason.
 */
class HorizontalPodAutoscalerSanitizer {

  private static final Map<String, Function<MetricSpec, MetricTarget>> TARGET_BY_METRIC_SOURCE =
      Map.of(
          "external", m -> m.getExternal() == null ? null : m.getExternal().getTarget(),
          "object", m -> m.getObject() == null ? null : m.getObject().getTarget(),
          "pods", m -> m.getPods() == null ? null : m.getPods().getTarget(),
          "resource", m -> m.getResource() == null ? null : m.getResource().getTarget(),
          "containerResource",
              m -> m.getContainerResource() == null ? null : m.getContainerResource().getTarget());

  static void sanitizeHorizontalPodAutoscalerSpec(
      final Map<String, Object> actualMap,
      final HorizontalPodAutoscalerSpec actualSpec,
      final HorizontalPodAutoscalerSpec desiredSpec) {
    if (actualSpec == null || desiredSpec == null) {
      return;
    }
    final List<MetricSpec> actualMetrics = actualSpec.getMetrics();
    final List<MetricSpec> desiredMetrics = desiredSpec.getMetrics();
    if (actualMetrics == null
        || desiredMetrics == null
        || actualMetrics.size() != desiredMetrics.size()) {
      return;
    }
    for (int metricIndex = 0; metricIndex < desiredMetrics.size(); metricIndex++) {
      final var actualMetric = actualMetrics.get(metricIndex);
      final var desiredMetric = desiredMetrics.get(metricIndex);
      for (var source : TARGET_BY_METRIC_SOURCE.entrySet()) {
        sanitizeTarget(
            actualMap,
            metricIndex,
            source.getKey(),
            source.getValue().apply(actualMetric),
            source.getValue().apply(desiredMetric));
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void sanitizeTarget(
      final Map<String, Object> actualMap,
      final int metricIndex,
      final String metricSource,
      final MetricTarget actualTarget,
      final MetricTarget desiredTarget) {
    if (actualTarget == null || desiredTarget == null) {
      return;
    }
    Optional.ofNullable(
            GenericKubernetesResource.get(
                actualMap, "spec", "metrics", metricIndex, metricSource, "target"))
        .map(Map.class::cast)
        .ifPresent(
            m -> {
              QuantitySanitizer.sanitizeQuantity(
                  m, "value", actualTarget.getValue(), desiredTarget.getValue());
              QuantitySanitizer.sanitizeQuantity(
                  m,
                  "averageValue",
                  actualTarget.getAverageValue(),
                  desiredTarget.getAverageValue());
            });
  }
}
