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
package io.javaoperatorsdk.operator.baseapi.createupdateeventfilter;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.createupdateeventfilter.CreateUpdateEventFilterTestReconciler.CONFIG_MAP_TEST_DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CreateUpdateInformerEventSourceEventFilterIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new CreateUpdateEventFilterTestReconciler())
          .build();

  @Test
  void updateEventNotReceivedAfterCreateOrUpdate() {
    CreateUpdateEventFilterTestCustomResource resource =
        CreateUpdateInformerEventSourceEventFilterIT.prepareTestResource();
    var createdResource = operator.create(resource);

    assertData(operator, createdResource, 1, 1);

    CreateUpdateEventFilterTestCustomResource actualCreatedResource =
        operator.get(
            CreateUpdateEventFilterTestCustomResource.class, resource.getMetadata().getName());
    actualCreatedResource.getSpec().setValue("2");
    operator.replace(actualCreatedResource);

    assertData(operator, actualCreatedResource, 2, 2);
  }

  static void assertData(
      LocallyRunOperatorExtension operator,
      CreateUpdateEventFilterTestCustomResource resource,
      int minExecutions,
      int maxExecutions) {
    await()
        .atMost(Duration.ofSeconds(1))
        .until(
            () -> {
              var cm = operator.get(ConfigMap.class, resource.getMetadata().getName());
              if (cm == null) {
                return false;
              }
              return cm.getData()
                  .get(CONFIG_MAP_TEST_DATA_KEY)
                  .equals(resource.getSpec().getValue());
            });

    int numberOfExecutions =
        ((CreateUpdateEventFilterTestReconciler) operator.getFirstReconciler())
            .getNumberOfExecutions();
    assertThat(numberOfExecutions).isGreaterThanOrEqualTo(minExecutions);
    assertThat(numberOfExecutions).isLessThanOrEqualTo(maxExecutions);
  }

  static CreateUpdateEventFilterTestCustomResource prepareTestResource() {
    CreateUpdateEventFilterTestCustomResource resource =
        new CreateUpdateEventFilterTestCustomResource();
    resource.setMetadata(new ObjectMeta());
    resource.getMetadata().setName("test1");
    resource.setSpec(new CreateUpdateEventFilterTestCustomResourceSpec());
    resource.getSpec().setValue("1");
    return resource;
  }
}
