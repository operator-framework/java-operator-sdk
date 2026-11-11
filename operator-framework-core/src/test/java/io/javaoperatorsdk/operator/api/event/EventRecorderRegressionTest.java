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
package io.javaoperatorsdk.operator.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.EventList;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.MicroTime;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.V1APIGroupDSL;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

import static io.javaoperatorsdk.operator.api.config.LeaderElectionConfigurationBuilder.aLeaderElectionConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Regression tests for the aggregation, naming and namespacing of recorded events. */
class EventRecorderRegressionTest {

  private final List<Event> emitted = new ArrayList<>();

  /**
   * The core/v1 API server (legacyValidateEvent in pkg/apis/core/validation/events.go) rejects an
   * event whose involvedObject.namespace is empty unless the event itself lives in "" or "default",
   * or in "kube-system" if it carries an eventTime, and with it an action. No other namespace is
   * accepted.
   */
  @Test
  void clusterScopedEventsInTheKubeSystemNamespaceCarryAnEventTimeAndAnAction() {
    var recorder = new DefaultEventRecorder((event, context) -> emitted.add(event));

    recorder.record(
        clusterScoped("ns1"), EventRecord.normal("Created", "created"), controller("kube-system"));

    var event = emitted.get(0);
    assertThat(event.getInvolvedObject().getNamespace()).isNull();
    assertThat(event.getMetadata().getNamespace()).isEqualTo("kube-system");
    assertThat(event.getEventTime().getTime())
        .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{6}Z");
    assertThat(event.getAction()).isEqualTo("Created");
  }

  @Test
  void aSuppliedActionIsKeptOnClusterScopedEventsInTheKubeSystemNamespace() {
    var recorder = new DefaultEventRecorder((event, context) -> emitted.add(event));

    recorder.record(
        clusterScoped("ns1"),
        EventRecord.builder()
            .type(EventType.NORMAL)
            .reason("Created")
            .message("created")
            .action("Create")
            .build(),
        controller("kube-system"));

    assertThat(emitted.get(0).getAction()).isEqualTo("Create");
  }

  @Test
  void clusterScopedEventsInTheDefaultNamespaceDoNotCarryAnEventTime() {
    var recorder = new DefaultEventRecorder((event, context) -> emitted.add(event));

    recorder.record(
        clusterScoped("ns1"), EventRecord.normal("Created", "created"), controller("default"));

    assertThat(emitted.get(0).getEventTime()).isNull();
    assertThat(emitted.get(0).getMetadata().getNamespace()).isEqualTo("default");
  }

  /**
   * A naming strategy (here the one EventNamingStrategy's javadoc suggests, a fixed suffix per
   * object) makes the name the aggregation identity on its own, so records with different type and
   * reason resolve to the same event. DefaultEventSink patches type and reason along with count,
   * lastTimestamp and message, so the event describes the latest occurrence.
   */
  @Test
  void aggregatingOntoANamedEventTakesTheTypeAndReasonOfTheLatestOccurrence() {
    var stored = new AtomicReference<Event>();
    var patched = new AtomicReference<Event>();
    var sink = new DefaultEventSink(mockClient(stored, patched));
    var recorder =
        DefaultEventRecorder.builder(sink)
            .namingStrategy(
                (regarding, record) ->
                    Optional.of(regarding.getMetadata().getName() + "-status-report"))
            .build();
    var controller = controller("default");

    recorder.record(configMap("test1"), EventRecord.normal("Ready", "all good"), controller);
    recorder.record(
        configMap("test1"), EventRecord.warning("Degraded", "replica down"), controller);

    assertThat(patched.get()).isNotNull();
    assertThat(patched.get().getCount()).isEqualTo(2);
    assertThat(patched.get().getMessage()).isEqualTo("replica down");
    assertThat(patched.get().getType()).isEqualTo("Warning");
    assertThat(patched.get().getReason()).isEqualTo("Degraded");
  }

  @Test
  void aggregatingOntoAnEventWithAnEventTimeUpdatesTypeReasonAndAction() {
    var stored = new AtomicReference<Event>();
    var patched = new AtomicReference<Event>();
    var sink = new DefaultEventSink(mockClient(stored, patched));
    var recorder =
        DefaultEventRecorder.builder(sink)
            .namingStrategy((regarding, record) -> Optional.of("fixed-name"))
            .build();
    var controller = controller("kube-system");

    recorder.record(clusterScoped("ns1"), EventRecord.normal("Ready", "all good"), controller);
    recorder.record(
        clusterScoped("ns1"), EventRecord.warning("Degraded", "replica down"), controller);

    assertThat(stored.get().getEventTime()).isNotNull();
    assertThat(patched.get().getCount()).isEqualTo(2);
    assertThat(patched.get().getType()).isEqualTo("Warning");
    assertThat(patched.get().getReason()).isEqualTo("Degraded");
    assertThat(patched.get().getAction()).isEqualTo("Degraded");
  }

  @Test
  void aggregatingOntoANamedEventWithoutAnActionClearsTheActionOfTheEarlierOccurrence() {
    var stored = new AtomicReference<Event>();
    var patched = new AtomicReference<Event>();
    var sink = new DefaultEventSink(mockClient(stored, patched));
    var recorder =
        DefaultEventRecorder.builder(sink)
            .namingStrategy((regarding, record) -> Optional.of("fixed-name"))
            .build();
    var controller = controller("default");

    recorder.record(
        configMap("test1"),
        EventRecord.builder()
            .type(EventType.NORMAL)
            .reason("Created")
            .message("created")
            .action("Create")
            .build(),
        controller);
    recorder.record(
        configMap("test1"), EventRecord.warning("Degraded", "replica down"), controller);

    assertThat(patched.get().getAction()).isNull();
  }

  @Test
  void aggregatingOntoAnEventWithAnEventTimeWithoutAnActionFallsBackToTheReason() {
    var stored = new AtomicReference<Event>();
    var patched = new AtomicReference<Event>();
    stored.set(
        new EventBuilder()
            .withNewMetadata()
            .withName("fixed-name")
            .withNamespace("default")
            .endMetadata()
            .withEventTime(new MicroTime("2026-01-01T00:00:00.000000Z"))
            .withAction("Create")
            .withCount(1)
            .build());
    var sink = new DefaultEventSink(mockClient(stored, patched));
    var recorder =
        DefaultEventRecorder.builder(sink)
            .namingStrategy((regarding, record) -> Optional.of("fixed-name"))
            .build();

    recorder.record(
        configMap("test1"), EventRecord.warning("Degraded", "replica down"), controller("default"));

    assertThat(patched.get().getEventTime()).isNotNull();
    assertThat(patched.get().getAction()).isEqualTo("Degraded");
  }

  @Test
  void shortenedGeneratedNamesDoNotEndInASeparatorBeforeTheHash() {
    var recorder = new DefaultEventRecorder((event, context) -> emitted.add(event));

    recorder.record(
        configMap("a".repeat(219) + "-" + "b".repeat(10)),
        EventRecord.normal("Created", "created"),
        controller("default"));

    assertThat(emitted.get(0).getMetadata().getName()).matches("a{219}\\.[0-9a-f]{32}");
  }

  /**
   * Supplied names longer than 253 characters are shortened with a hash of the full name, so two
   * different events of a long-named object are not merged into one.
   */
  @Test
  void longSuppliedNamesStayDistinct() {
    var recorder =
        DefaultEventRecorder.builder((event, context) -> emitted.add(event))
            .namingStrategy(
                (regarding, record) ->
                    Optional.of(
                        regarding.getMetadata().getName() + "-" + record.reason().toLowerCase()))
            .build();
    var controller = controller("default");

    recorder.record(
        configMap("a".repeat(245)),
        EventRecord.warning("BackoffLimitExceeded", "job failed"),
        controller);
    recorder.record(
        configMap("a".repeat(245)),
        EventRecord.warning("BackoffStarted", "job retrying"),
        controller);

    assertThat(emitted.get(0).getMetadata().getName())
        .isNotEqualTo(emitted.get(1).getMetadata().getName());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private KubernetesClient mockClient(
      AtomicReference<Event> stored, AtomicReference<Event> patched) {
    var client = mock(KubernetesClient.class);
    var v1 = mock(V1APIGroupDSL.class);
    MixedOperation<Event, EventList, Resource<Event>> events = mock(MixedOperation.class);
    NonNamespaceOperation<Event, EventList, Resource<Event>> inNs =
        mock(NonNamespaceOperation.class);
    when(client.v1()).thenReturn(v1);
    when(v1.events()).thenReturn(events);
    when(events.inNamespace(anyString())).thenReturn(inNs);
    Resource<Event> byName = mock(Resource.class);
    when(inNs.withName(anyString())).thenReturn(byName);
    when(byName.get()).thenAnswer(i -> stored.get());
    when(inNs.resource(any(Event.class)))
        .thenAnswer(
            i -> {
              Event target = i.getArgument(0);
              Resource<Event> r = mock(Resource.class);
              when(r.create())
                  .thenAnswer(
                      c -> {
                        stored.set(target);
                        return target;
                      });
              when(r.patch(any(Event.class)))
                  .thenAnswer(
                      p -> {
                        Event e = p.getArgument(0);
                        patched.set(e);
                        stored.set(e);
                        return e;
                      });
              return r;
            });
    return client;
  }

  @SuppressWarnings("rawtypes")
  private ControllerConfiguration<?> controller(String clusterScopedEventNamespace) {
    var configurationService = mock(ConfigurationService.class);
    when(configurationService.getLeaderElectionConfiguration())
        .thenReturn(
            Optional.of(aLeaderElectionConfiguration("lease").withIdentity("pod-1").build()));
    when(configurationService.clusterScopedEventNamespace())
        .thenReturn(clusterScopedEventNamespace);
    ControllerConfiguration controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getName()).thenReturn("testcontroller");
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    return controllerConfiguration;
  }

  private HasMetadata clusterScoped(String name) {
    return new NamespaceBuilder()
        .withNewMetadata()
        .withName(name)
        .withUid("uid-3")
        .endMetadata()
        .build();
  }

  private ConfigMap configMap(String name) {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(name)
        .withNamespace("ns1")
        .withUid("uid-1")
        .withResourceVersion("42")
        .endMetadata()
        .build();
  }
}
