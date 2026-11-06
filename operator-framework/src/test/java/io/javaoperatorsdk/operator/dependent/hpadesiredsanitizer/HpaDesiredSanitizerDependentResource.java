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
package io.javaoperatorsdk.operator.dependent.hpadesiredsanitizer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpecBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer.StatefulSetDesiredSanitizerCustomResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class HpaDesiredSanitizerDependentResource
    extends CRUDKubernetesDependentResource<
        HorizontalPodAutoscaler, StatefulSetDesiredSanitizerCustomResource> {

  public static final Set<String> matchedPrimaryValues = ConcurrentHashMap.newKeySet();
  public static volatile boolean nonMatchedAtLeastOnce;

  @Override
  protected HorizontalPodAutoscaler desired(
      StatefulSetDesiredSanitizerCustomResource primary,
      Context<StatefulSetDesiredSanitizerCustomResource> context) {
    var name = primary.getMetadata().getName();
    return new HorizontalPodAutoscalerBuilder()
        .withNewMetadata()
        .withName(name)
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withNewSpec()
        .withMinReplicas(1)
        .withMaxReplicas(4)
        .withNewScaleTargetRef()
        .withApiVersion("apps/v1")
        .withKind("Deployment")
        .withName(name)
        .endScaleTargetRef()
        .withMetrics(
            // the API server stores 1000 as 1k and 0.5 as 500m
            new MetricSpecBuilder()
                .withType("External")
                .withNewExternal()
                .withNewMetric()
                .withName("queue-depth")
                .endMetric()
                .withNewTarget()
                .withType("Value")
                .withValue(new Quantity("1000"))
                .endTarget()
                .endExternal()
                .build(),
            new MetricSpecBuilder()
                .withType("Pods")
                .withNewPods()
                .withNewMetric()
                .withName("packets-per-second")
                .endMetric()
                .withNewTarget()
                .withType("AverageValue")
                .withAverageValue(new Quantity("0.5"))
                .endTarget()
                .endPods()
                .build())
        .endSpec()
        .build();
  }

  @Override
  public Result<HorizontalPodAutoscaler> match(
      HorizontalPodAutoscaler actualResource,
      StatefulSetDesiredSanitizerCustomResource primary,
      Context<StatefulSetDesiredSanitizerCustomResource> context) {
    var res = super.match(actualResource, primary, context);
    if (res.matched()) {
      matchedPrimaryValues.add(primary.getSpec().getValue());
    } else {
      nonMatchedAtLeastOnce = true;
    }
    return res;
  }
}
