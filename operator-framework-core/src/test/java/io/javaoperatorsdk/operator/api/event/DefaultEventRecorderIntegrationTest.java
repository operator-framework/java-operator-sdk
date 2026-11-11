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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.fabric8.kubeapitest.junit.EnableKubeAPIServer;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

import static io.javaoperatorsdk.operator.api.config.LeaderElectionConfigurationBuilder.aLeaderElectionConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Records events against a real API server, which validates and aggregates them as in a cluster.
 */
@EnableKubeAPIServer
class DefaultEventRecorderIntegrationTest {

  static KubernetesClient client;

  @Test
  void countsRepeatedEventsAboutANamespacedResource() {
    var configMap = configMap("test1");
    var controller = controller("default");

    recorder().record(configMap, EventRecord.normal("Ready", "all good"), controller);
    recorder().record(configMap, EventRecord.normal("Ready", "all good"), controller);

    var events = eventsAbout(configMap, configMap.getMetadata().getNamespace());
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getCount()).isEqualTo(2);
  }

  @Test
  void countsRepeatedEventsAboutAClusterScopedResourceInTheDefaultNamespace() {
    var namespace = clusterScoped();
    var controller = controller("default");

    recorder().record(namespace, EventRecord.normal("Ready", "all good"), controller);
    recorder().record(namespace, EventRecord.normal("Ready", "all good"), controller);

    var events = eventsAbout(namespace, "default");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getCount()).isEqualTo(2);
    assertThat(events.get(0).getEventTime()).isNull();
  }

  @Test
  void countsRepeatedEventsAboutAClusterScopedResourceInTheKubeSystemNamespace() {
    var namespace = clusterScoped();
    var controller = controller("kube-system");

    recorder().record(namespace, EventRecord.normal("Ready", "all good"), controller);
    recorder().record(namespace, EventRecord.normal("Ready", "all good"), controller);

    var events = eventsAbout(namespace, "kube-system");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getCount()).isEqualTo(2);
    assertThat(events.get(0).getEventTime()).isNotNull();
    assertThat(events.get(0).getAction()).isEqualTo("Ready");
  }

  @Test
  void clusterScopedEventsInOtherNamespacesAreRejectedByTheApiServer() {
    client
        .namespaces()
        .resource(
            new NamespaceBuilder().withNewMetadata().withName("operator-ns").endMetadata().build())
        .serverSideApply();
    var namespace = clusterScoped();
    var controller = controller("operator-ns");
    var event = recorder().toEvent(namespace, EventRecord.normal("Ready", "all good"), controller);

    assertThatThrownBy(() -> new DefaultEventSink(client).emit(event, controller))
        .isInstanceOf(KubernetesClientException.class);
    assertThat(eventsAbout(namespace, "operator-ns")).isEmpty();
  }

  @Test
  void aggregatingOntoANamedEventTakesTheTypeReasonAndActionOfTheLatestOccurrence() {
    var configMap = configMap("test2");
    var controller = controller("default");
    var recorder = namedRecorder("named-event-" + UUID.randomUUID());

    recorder.record(
        configMap,
        EventRecord.builder()
            .type(EventType.NORMAL)
            .reason("Ready")
            .message("all good")
            .action("Create")
            .build(),
        controller);
    recorder.record(configMap, EventRecord.warning("Degraded", "replica down"), controller);

    var events = eventsAbout(configMap, "default");
    assertThat(events).hasSize(1);
    var event = events.get(0);
    assertThat(event.getCount()).isEqualTo(2);
    assertThat(event.getType()).isEqualTo("Warning");
    assertThat(event.getReason()).isEqualTo("Degraded");
    assertThat(event.getMessage()).isEqualTo("replica down");
    assertThat(event.getAction()).isNull();
  }

  @Test
  void aggregatingOntoANamedEventWithAnEventTimeTakesTheTypeReasonAndActionOfTheLatestOccurrence() {
    var namespace = clusterScoped();
    var controller = controller("kube-system");
    var recorder = namedRecorder("named-event-" + UUID.randomUUID());

    recorder.record(namespace, EventRecord.normal("Ready", "all good"), controller);
    recorder.record(namespace, EventRecord.warning("Degraded", "replica down"), controller);

    var events = eventsAbout(namespace, "kube-system");
    assertThat(events).hasSize(1);
    var event = events.get(0);
    assertThat(event.getCount()).isEqualTo(2);
    assertThat(event.getType()).isEqualTo("Warning");
    assertThat(event.getReason()).isEqualTo("Degraded");
    assertThat(event.getAction()).isEqualTo("Degraded");
  }

  @Test
  void longSuppliedNamesAreAcceptedAndStayDistinct() {
    var configMap = configMap("test3");
    var controller = controller("default");
    var prefix = "a".repeat(219) + "-";
    var recorder =
        DefaultEventRecorder.builder(new DefaultEventSink(client))
            .namingStrategy(
                (regarding, record) -> Optional.of(prefix + record.reason().toLowerCase() + "x"))
            .build();

    recorder.record(configMap, EventRecord.normal("First", "first"), controller);
    recorder.record(configMap, EventRecord.normal("Second", "second"), controller);

    var events = eventsAbout(configMap, "default");
    assertThat(events).hasSize(2);
    assertThat(events)
        .allSatisfy(event -> assertThat(event.getMetadata().getName()).hasSizeLessThan(254));
  }

  @Test
  void generatedNamesShortenedAtASeparatorAreAccepted() {
    var configMap = configMap("a".repeat(219) + "-" + "b".repeat(10));
    var controller = controller("default");

    recorder().record(configMap, EventRecord.normal("Ready", "all good"), controller);

    assertThat(eventsAbout(configMap, "default")).hasSize(1);
  }

  private static DefaultEventRecorder recorder() {
    return new DefaultEventRecorder(new DefaultEventSink(client));
  }

  private static DefaultEventRecorder namedRecorder(String name) {
    return DefaultEventRecorder.builder(new DefaultEventSink(client))
        .namingStrategy((regarding, record) -> Optional.of(name))
        .build();
  }

  private static List<Event> eventsAbout(HasMetadata resource, String namespace) {
    return client
        .v1()
        .events()
        .inNamespace(namespace)
        .withField("involvedObject.uid", resource.getMetadata().getUid())
        .list()
        .getItems();
  }

  private static HasMetadata configMap(String name) {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(name)
        .withNamespace("default")
        .withUid(UUID.randomUUID().toString())
        .endMetadata()
        .build();
  }

  private static HasMetadata clusterScoped() {
    return new NamespaceBuilder()
        .withNewMetadata()
        .withName("ns-" + UUID.randomUUID())
        .withUid(UUID.randomUUID().toString())
        .endMetadata()
        .build();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static ControllerConfiguration<?> controller(String clusterScopedNamespace) {
    var configurationService = mock(ConfigurationService.class);
    when(configurationService.getLeaderElectionConfiguration())
        .thenReturn(
            Optional.of(aLeaderElectionConfiguration("lease").withIdentity("pod-1").build()));
    when(configurationService.clusterScopedEventNamespace()).thenReturn(clusterScopedNamespace);
    ControllerConfiguration controllerConfiguration = mock(ControllerConfiguration.class);
    when(controllerConfiguration.getName()).thenReturn("testcontroller");
    when(controllerConfiguration.getConfigurationService()).thenReturn(configurationService);
    return controllerConfiguration;
  }
}
