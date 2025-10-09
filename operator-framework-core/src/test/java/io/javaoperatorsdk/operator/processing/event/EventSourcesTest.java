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
package io.javaoperatorsdk.operator.processing.event;

import java.util.ConcurrentModificationException;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.MockKubernetesClient;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.MockControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class EventSourcesTest {

  public static final String EVENT_SOURCE_NAME = "foo";

  @Test
  void cannotAddTwoDifferentEventSourcesWithSameName() {
    final var eventSources = new EventSources();
    var es1 = mock(EventSource.class);
    when(es1.name()).thenReturn(EVENT_SOURCE_NAME);
    when(es1.resourceType()).thenReturn(EventSource.class);
    var es2 = mock(EventSource.class);
    when(es2.name()).thenReturn(EVENT_SOURCE_NAME);
    when(es2.resourceType()).thenReturn(EventSource.class);

    eventSources.add(es1);
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          eventSources.add(es2);
        });
  }

  @Test
  void cannotAddTwoEventSourcesWithSame() {
    final var eventSources = new EventSources();
    final var source = mock(EventSource.class);
    when(source.name()).thenReturn("name");
    when(source.resourceType()).thenReturn(EventSource.class);

    eventSources.add(source);
    assertThrows(IllegalArgumentException.class, () -> eventSources.add(source));
  }

  @Test
  void eventSourcesStreamShouldNotReturnControllerEventSource() {
    final var eventSources = new EventSources();
    final var source = mock(EventSource.class);
    when(source.name()).thenReturn(EVENT_SOURCE_NAME);
    when(source.resourceType()).thenReturn(EventSource.class);

    eventSources.add(source);

    assertThat(eventSources.additionalEventSources())
        .containsExactly(eventSources.retryEventSource(), source);
  }

  @Test
  void additionalEventSourcesShouldNotContainNamedEventSources() {
    final var eventSources = new EventSources();
    final var source = mock(EventSource.class);
    when(source.name()).thenReturn(EVENT_SOURCE_NAME);
    when(source.resourceType()).thenReturn(EventSource.class);
    eventSources.add(source);

    assertThat(eventSources.additionalEventSources())
        .containsExactly(eventSources.retryEventSource(), source);
  }

  @Test
  void checkControllerEventSource() {
    final var eventSources = new EventSources();
    final var configuration = MockControllerConfiguration.forResource(HasMetadata.class);
    when(configuration.getConfigurationService()).thenReturn(new BaseConfigurationService());
    final var controller =
        new Controller(
            mock(Reconciler.class), configuration, MockKubernetesClient.client(HasMetadata.class));
    eventSources.createControllerEventSource(controller);
    final var controllerEventSource = eventSources.controllerEventSource();
    assertNotNull(controllerEventSource);
    assertEquals(HasMetadata.class, controllerEventSource.resourceType());

    assertEquals(controllerEventSource, eventSources.controllerEventSource());
  }

  @Test
  void flatMappedSourcesShouldReturnOnlyUserRegisteredEventSources() {
    final var eventSources = new EventSources();
    final var mock1 = eventSourceMockWithName(EventSource.class, "name1", HasMetadata.class);
    final var mock2 = eventSourceMockWithName(EventSource.class, "name2", HasMetadata.class);
    final var mock3 = eventSourceMockWithName(EventSource.class, "name3", ConfigMap.class);

    eventSources.add(mock1);
    eventSources.add(mock2);
    eventSources.add(mock3);

    assertThat(eventSources.flatMappedSources()).contains(mock1, mock2, mock3);
  }

  @Test
  void clearShouldWork() {
    final var eventSources = new EventSources();
    final var mock1 = eventSourceMockWithName(EventSource.class, "name1", HasMetadata.class);
    final var mock2 = eventSourceMockWithName(EventSource.class, "name2", HasMetadata.class);
    final var mock3 = eventSourceMockWithName(EventSource.class, "name3", ConfigMap.class);

    eventSources.add(mock1);
    eventSources.add(mock2);
    eventSources.add(mock3);

    eventSources.clear();
    assertThat(eventSources.flatMappedSources()).isEmpty();
  }

  @Test
  void getShouldWork() {
    final var eventSources = new EventSources();
    final var mock1 = eventSourceMockWithName(EventSource.class, "name1", HasMetadata.class);
    final var mock2 = eventSourceMockWithName(EventSource.class, "name2", HasMetadata.class);
    final var mock3 = eventSourceMockWithName(EventSource.class, "name3", ConfigMap.class);

    eventSources.add(mock1);
    eventSources.add(mock2);
    eventSources.add(mock3);

    assertEquals(mock1, eventSources.get(HasMetadata.class, "name1"));
    assertEquals(mock2, eventSources.get(HasMetadata.class, "name2"));
    assertEquals(mock3, eventSources.get(ConfigMap.class, "name3"));
    assertEquals(mock3, eventSources.get(ConfigMap.class, null));

    assertThrows(IllegalArgumentException.class, () -> eventSources.get(HasMetadata.class, null));
    assertThrows(
        IllegalArgumentException.class, () -> eventSources.get(ConfigMap.class, "unknown"));
    assertThrows(IllegalArgumentException.class, () -> eventSources.get(null, null));
    assertThrows(IllegalArgumentException.class, () -> eventSources.get(HasMetadata.class, null));
  }

  @Test
  void getEventSourcesShouldWork() {
    final var eventSources = new EventSources();
    final var mock1 = eventSourceMockWithName(EventSource.class, "name1", HasMetadata.class);
    final var mock2 = eventSourceMockWithName(EventSource.class, "name2", HasMetadata.class);
    final var mock3 = eventSourceMockWithName(EventSource.class, "name3", ConfigMap.class);

    eventSources.add(mock1);
    eventSources.add(mock2);
    eventSources.add(mock3);

    var sources = eventSources.getEventSources(HasMetadata.class);
    assertThat(sources.size()).isEqualTo(2);
    assertThat(sources).contains(mock1, mock2);

    sources = eventSources.getEventSources(ConfigMap.class);
    assertThat(sources.size()).isEqualTo(1);
    assertThat(sources).contains(mock3);

    assertThat(eventSources.getEventSources(Service.class)).isEmpty();
  }

  @Test
  void testConcurrentAddRemoveAndGet() throws InterruptedException {

    final var concurrentExceptionFound = new AtomicBoolean(false);

    for (int i = 0; i < 1000 && !concurrentExceptionFound.get(); i++) {
      final var eventSources = new EventSources();
      var eventSourceList =
          IntStream.range(1, 20)
              .mapToObj(
                  n -> eventSourceMockWithName(EventSource.class, "name" + n, HasMetadata.class))
              .toList();

      IntStream.range(1, 10).forEach(n -> eventSources.add(eventSourceList.get(n - 1)));

      var phaser = new Phaser(2);

      var t1 =
          new Thread(
              () -> {
                phaser.arriveAndAwaitAdvance();
                IntStream.range(11, 20).forEach(n -> eventSources.add(eventSourceList.get(n - 1)));
              });
      var t2 =
          new Thread(
              () -> {
                phaser.arriveAndAwaitAdvance();
                try {
                  eventSources.getEventSources(eventSourceList.get(0).resourceType());
                } catch (ConcurrentModificationException e) {
                  concurrentExceptionFound.set(true);
                }
              });
      t1.start();
      t2.start();
      t1.join();
      t2.join();
    }

    assertThat(concurrentExceptionFound)
        .withFailMessage("ConcurrentModificationException thrown")
        .isFalse();
  }

  <T extends EventSource> EventSource eventSourceMockWithName(
      Class<T> clazz, String name, Class resourceType) {
    var mockedES = mock(clazz);
    when(mockedES.name()).thenReturn(name);
    when(mockedES.resourceType()).thenReturn(resourceType);
    return mockedES;
  }
}
