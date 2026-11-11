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

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;

/**
 * The views an {@link EventRecorder} offers are default methods, so that an implementation only has
 * to record. This exercises them on a minimal implementation that only records what it was asked.
 */
class EventRecorderTest {

  private record Recording(
      HasMetadata regarding, EventRecord event, ControllerConfiguration<?> controller) {}

  private final List<Recording> recordings = new ArrayList<>();
  private final EventRecorder recorder =
      (regarding, event, controller) -> recordings.add(new Recording(regarding, event, controller));

  private final ControllerConfiguration<?> controller = mock(ControllerConfiguration.class);
  private final ConfigMap primary = configMap("primary");
  private final ConfigMap other = configMap("other");

  @Test
  void theViewBoundToAnObjectRecordsAboutThatObjectForTheController() {
    var bound = recorder.forResource(other, controller);

    bound.normal("Created", "created");
    bound.warn("Failed", "failed");
    bound.record(EventRecord.builder().reason("Noticed").build());

    assertThat(recordings).hasSize(3);
    assertThat(recordings).allSatisfy(r -> assertThat(r.regarding()).isSameAs(other));
    assertThat(recordings).allSatisfy(r -> assertThat(r.controller()).isSameAs(controller));
    assertThat(recordings)
        .extracting(r -> r.event().type())
        .containsExactly(EventType.NORMAL, EventType.WARNING, EventType.NORMAL);
    assertThat(recordings)
        .extracting(r -> r.event().reason())
        .containsExactly("Created", "Failed", "Noticed");
  }

  @Test
  void theViewBoundToAControllerRecordsAboutAnyObject() {
    var bound = recorder.forController(controller);
    var event = EventRecord.normal("Noticed", "noticed");

    bound.record(other, event);
    bound.forResource(primary).warn("Failed", "failed");

    assertThat(recordings).hasSize(2);
    assertThat(recordings.get(0)).isEqualTo(new Recording(other, event, controller));
    assertThat(recordings.get(1).regarding()).isSameAs(primary);
    assertThat(recordings.get(1).event().reason()).isEqualTo("Failed");
    assertThat(recordings.get(1).controller()).isSameAs(controller);
  }

  @Test
  void viewsRejectMissingArguments() {
    assertThatNullPointerException().isThrownBy(() -> recorder.forResource(null, controller));
    assertThatNullPointerException().isThrownBy(() -> recorder.forResource(other, null));
    assertThatNullPointerException().isThrownBy(() -> recorder.forController(null));
    assertThatNullPointerException()
        .isThrownBy(() -> recorder.forController(controller).forResource(null));
  }

  private static ConfigMap configMap(String name) {
    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(name)
        .withNamespace("ns1")
        .withUid("uid-" + name)
        .endMetadata()
        .build();
  }
}
