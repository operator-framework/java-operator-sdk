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
package io.javaoperatorsdk.operator.baseapi.eventrecorderconfigured;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.api.event.DefaultEventRecorder;
import io.javaoperatorsdk.operator.api.event.DefaultEventSink;
import io.javaoperatorsdk.operator.api.event.EventKeyStrategy;
import io.javaoperatorsdk.operator.api.event.EventRecord;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.eventrecorderconfigured.ConfiguredEventRecorderReconciler.AGGREGATED_REASON;
import static io.javaoperatorsdk.operator.baseapi.eventrecorderconfigured.ConfiguredEventRecorderReconciler.NAMED_REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Configuring how the event recorder aggregates, names and owns events",
    description =
        """
        Demonstrates configuring the event recorder with a default aggregation key strategy, a \
        naming strategy and owner references. Aggregating by reason resolves repeated occurrences \
        with changing messages to one event whose count grows and whose message is replaced with \
        the latest one. A naming strategy gives events predictable names instead of the default \
        identity hash. Owner references relate the events to the object they are about.
        """)
class ConfiguredEventRecorderIT {

  private static final String TEST_RESOURCE_NAME = "test1";

  private final KubernetesClient sinkClient = new KubernetesClientBuilder().build();

  private final ConfiguredEventRecorderReconciler reconciler =
      new ConfiguredEventRecorderReconciler();

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(reconciler)
          .withConfigurationService(o -> o.withEventRecorder(configuredEventRecorder()))
          .build();

  @AfterEach
  void closeSinkClient() {
    sinkClient.close();
  }

  @Test
  void aggregatesOccurrencesWithChangingMessagesOntoOneEvent() {
    var resource = extension.create(testResource());
    await().untilAsserted(() -> assertThat(reconciler.getNumberOfExecutions()).isPositive());

    // reconcile once more: aggregating by reason resolves the new messages to the same events
    resource.getMetadata().setAnnotations(Map.of("reconcile", "again"));
    extension.replace(resource);

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isGreaterThanOrEqualTo(2);

              assertThat(eventsWithReason(AGGREGATED_REASON))
                  .singleElement()
                  .satisfies(
                      event -> {
                        assertThat(event.getCount()).isGreaterThanOrEqualTo(2);
                        assertThat(event.getMessage())
                            .isEqualTo("something changed in execution " + event.getCount());
                      });
            });
  }

  @Test
  void namesEventsThroughTheConfiguredNamingStrategy() {
    extension.create(testResource());

    await()
        .untilAsserted(
            () ->
                assertThat(eventsWithReason(NAMED_REASON))
                    .singleElement()
                    .extracting(event -> event.getMetadata().getName())
                    .isEqualTo(TEST_RESOURCE_NAME + "-status-report"));
  }

  @Test
  void setsOwnerReferencesOnTheEventsItRecords() {
    var resource = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var events = eventsForTestResource();
              assertThat(events).isNotEmpty();
              assertThat(events)
                  .allSatisfy(
                      event ->
                          assertThat(event.getMetadata().getOwnerReferences())
                              .singleElement()
                              .returns(
                                  "ConfiguredEventRecorderCustomResource", OwnerReference::getKind)
                              .returns(resource.getMetadata().getUid(), OwnerReference::getUid));
            });
  }

  private List<Event> eventsWithReason(String reason) {
    return eventsForTestResource().stream().filter(e -> reason.equals(e.getReason())).toList();
  }

  @SuppressWarnings("resource")
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

  private ConfiguredEventRecorderCustomResource testResource() {
    var resource = new ConfiguredEventRecorderCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return resource;
  }

  private DefaultEventRecorder configuredEventRecorder() {
    return DefaultEventRecorder.builder(new DefaultEventSink(sinkClient))
        .namingStrategy(ConfiguredEventRecorderIT::statusReportName)
        .keyStrategy(EventKeyStrategy.byReason())
        .ownerReference(true)
        .build();
  }

  private static Optional<String> statusReportName(HasMetadata regarding, EventRecord record) {
    return NAMED_REASON.equals(record.reason())
        ? Optional.of(regarding.getMetadata().getName() + "-status-report")
        : Optional.empty();
  }
}
