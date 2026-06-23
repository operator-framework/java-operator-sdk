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
package io.javaoperatorsdk.operator.baseapi.filter;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OrCombinedFilterIT {

  public static final String RESOURCE_NAME = "or-filter-test";
  public static final int POLL_DELAY = 150;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new OrCombinedFilterTestReconciler())
          .build();

  @Test
  void orCombinedFilterTriggersReconciliationEvenWhenInternalFilterWouldReject() {
    operator.create(createResource());

    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(reconciler().getNumberOfExecutions()).isEqualTo(1));

    // Spec update bumps generation — internal generation-aware filter accepts → reconcile
    var res = operator.get(FilterTestCustomResource.class, RESOURCE_NAME);
    res.getSpec().setValue("updated");
    operator.replace(res);

    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(reconciler().getNumberOfExecutions()).isEqualTo(2));

    // Annotation-only update does not bump generation — internal filter would reject,
    // but with OR combination the user filter accepts
    res = operator.get(FilterTestCustomResource.class, RESOURCE_NAME);
    res.getMetadata()
        .setAnnotations(Map.of(OrCombinedFilterTestReconciler.TRIGGER_ANNOTATION, "true"));
    operator.replace(res);

    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(reconciler().getNumberOfExecutions()).isEqualTo(3));

    // Removing the annotation: user filter rejects, no generation change, should not reconcile
    res = operator.get(FilterTestCustomResource.class, RESOURCE_NAME);
    res.getMetadata().getAnnotations().remove(OrCombinedFilterTestReconciler.TRIGGER_ANNOTATION);
    operator.replace(res);

    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(reconciler().getNumberOfExecutions()).isEqualTo(3));
  }

  private OrCombinedFilterTestReconciler reconciler() {
    return operator.getReconcilerOfType(OrCombinedFilterTestReconciler.class);
  }

  FilterTestCustomResource createResource() {
    var resource = new FilterTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    resource.setSpec(new FilterTestResourceSpec());
    resource.getSpec().setValue("initial");
    return resource;
  }
}
