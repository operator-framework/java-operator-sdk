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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Recording Kubernetes events from a reconciler",
    description =
        """
        Demonstrates recording Kubernetes events about the primary resource using the event \
        recorder available from the reconciliation context. Verifies that both normal and warning \
        events reach the cluster, refer to the primary resource as their involved object, and are \
        attributed to the reporting controller.
        """)
class EventRecorderIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new EventRecorderReconciler()).build();

  @Test
  void recordsEventsAboutThePrimaryResource() {
    extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var events = eventsForTestResource();
              assertThat(events)
                  .extracting(Event::getReason)
                  .contains(
                      EventRecorderReconciler.NORMAL_REASON,
                      EventRecorderReconciler.WARNING_REASON);

              var warning =
                  events.stream()
                      .filter(e -> EventRecorderReconciler.WARNING_REASON.equals(e.getReason()))
                      .findFirst()
                      .orElseThrow();
              assertThat(warning.getType()).isEqualTo("Warning");
              assertThat(warning.getMessage()).isEqualTo("this is a warning about the resource");
              assertThat(warning.getInvolvedObject().getKind())
                  .isEqualTo("EventRecorderCustomResource");
              assertThat(warning.getInvolvedObject().getName()).isEqualTo(TEST_RESOURCE_NAME);
              assertThat(warning.getReportingComponent()).isNotBlank();
              assertThat(warning.getReportingInstance()).isNotBlank();
            });
  }

  private List<Event> eventsForTestResource() {
    return extension
        .getKubernetesClient()
        .v1()
        .events()
        .inNamespace(extension.getNamespace())
        .withField("involvedObject.name", TEST_RESOURCE_NAME)
        .list()
        .getItems();
  }

  EventRecorderCustomResource testResource() {
    var resource = new EventRecorderCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return resource;
  }
}
