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
package io.javaoperatorsdk.operator.baseapi.eventrecorder;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.baseapi.clusterscopedresource.ClusterScopedCustomResource;
import io.javaoperatorsdk.operator.baseapi.clusterscopedresource.ClusterScopedCustomResourceSpec;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ClusterScopedEventRecorderIT {

  public static final String TEST_RESOURCE_NAME = "event-recorder-test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ClusterScopedEventRecorderReconciler())
          .build();

  @Test
  void recordsAndCountsEventsAboutAClusterScopedResourceInTheDefaultNamespace() {
    var resource = extension.create(testResource());
    var uid = resource.getMetadata().getUid();
    await().untilAsserted(() -> assertThat(eventsAbout(uid)).hasSize(1));

    resource.getMetadata().setAnnotations(Map.of("reconcile", "again"));
    extension.replace(resource);

    await()
        .untilAsserted(
            () -> {
              var events = eventsAbout(uid);
              assertThat(events).hasSize(1);
              assertThat(events.get(0).getReason())
                  .isEqualTo(ClusterScopedEventRecorderReconciler.REASON);
              assertThat(events.get(0).getInvolvedObject().getNamespace()).isNull();
              assertThat(events.get(0).getCount()).isGreaterThanOrEqualTo(2);
            });
  }

  private List<Event> eventsAbout(String uid) {
    return extension
        .getKubernetesClient()
        .v1()
        .events()
        .inNamespace("default")
        .withField("involvedObject.uid", uid)
        .list()
        .getItems();
  }

  ClusterScopedCustomResource testResource() {
    var resource = new ClusterScopedCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    resource.setSpec(new ClusterScopedCustomResourceSpec());
    resource.getSpec().setTargetNamespace(extension.getNamespace());
    resource.getSpec().setData("data");
    return resource;
  }
}
