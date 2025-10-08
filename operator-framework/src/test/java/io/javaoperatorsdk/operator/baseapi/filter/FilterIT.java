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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.filter.FilterTestReconciler.CONFIG_MAP_FILTER_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FilterIT {

  public static final String RESOURCE_NAME = "test1";
  public static final int POLL_DELAY = 150;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(FilterTestReconciler.class).build();

  @Test
  void filtersControllerResourceUpdate() {
    var res = operator.create(createResource());
    // One for CR create event other for ConfigMap event
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(FilterTestReconciler.class)
                            .getNumberOfExecutions())
                    .isEqualTo(2));

    res.getSpec().setValue(FilterTestReconciler.CUSTOM_RESOURCE_FILTER_VALUE);
    operator.replace(res);

    // not more reconciliation with the filtered value
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(FilterTestReconciler.class)
                            .getNumberOfExecutions())
                    .isEqualTo(2));
  }

  @Test
  void filtersSecondaryResourceUpdate() {
    var res = operator.create(createResource());
    // One for CR create event other for ConfigMap event
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(FilterTestReconciler.class)
                            .getNumberOfExecutions())
                    .isEqualTo(2));

    res.getSpec().setValue(CONFIG_MAP_FILTER_VALUE);
    operator.replace(res);

    // the CM event filtered out
    await()
        .pollDelay(Duration.ofMillis(POLL_DELAY))
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(FilterTestReconciler.class)
                            .getNumberOfExecutions())
                    .isEqualTo(3));
  }

  FilterTestCustomResource createResource() {
    FilterTestCustomResource resource = new FilterTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    resource.setSpec(new FilterTestResourceSpec());
    resource.getSpec().setValue("value1");
    return resource;
  }
}
