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

class OrFilterIT {

  public static final String RESOURCE_NAME = "or-filter-test";
  public static final int POLL_DELAY = 150;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new OrFilterTestReconciler()).build();

  @Test
  void orFilterTriggersReconciliationEvenWhenInternalFilterWouldReject() {
    var res = operator.create(createResource());

    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(reconciler().getNumberOfExecutions()).isEqualTo(1));

    // Spec update bumps generation — internal generation-aware filter accepts -> reconcile
    res = operator.get(FilterTestCustomResource.class, RESOURCE_NAME);
    res.getSpec().setValue("updated");
    operator.replace(res);

    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(reconciler().getNumberOfExecutions()).isEqualTo(2));

    // Annotation-only update does not bump generation — internal filter would reject,
    // but the OR filter accepts -> reconcile still happens
    res = operator.get(FilterTestCustomResource.class, RESOURCE_NAME);
    res.getMetadata().setAnnotations(Map.of(OrFilterTestReconciler.TRIGGER_ANNOTATION, "true"));
    operator.replace(res);

    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(reconciler().getNumberOfExecutions()).isEqualTo(3));

    // Removing the annotation: OR filter rejects, no generation change -> no reconcile
    res = operator.get(FilterTestCustomResource.class, RESOURCE_NAME);
    res.getMetadata().getAnnotations().remove(OrFilterTestReconciler.TRIGGER_ANNOTATION);
    operator.replace(res);

    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(() -> assertThat(reconciler().getNumberOfExecutions()).isEqualTo(3));
  }

  private OrFilterTestReconciler reconciler() {
    return operator.getReconcilerOfType(OrFilterTestReconciler.class);
  }

  FilterTestCustomResource createResource() {
    var resource = new FilterTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    resource.setSpec(new FilterTestResourceSpec());
    resource.getSpec().setValue("initial");
    return resource;
  }
}
