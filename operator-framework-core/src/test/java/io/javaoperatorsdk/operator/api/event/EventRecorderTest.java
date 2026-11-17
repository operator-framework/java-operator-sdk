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
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The views an {@link EventRecorder} offers are default methods, so that an implementation only has
 * to record. This exercises them on a minimal implementation that only records what it was asked.
 */
class EventRecorderTest {

  private record Recording(HasMetadata regarding, EventRecord event, Context<?> context) {}

  private final List<Recording> recordings = new ArrayList<>();
  private final EventRecorder recorder =
      (regarding, event, context) -> recordings.add(new Recording(regarding, event, context));

  private final ConfigMap primary = configMap("primary");
  private final ConfigMap other = configMap("other");
  private final Context<?> context = context();

  @Test
  void recordingWithoutAnObjectIsAboutThePrimaryResourceOfTheContext() {
    var event = EventRecord.normal("Created", "created");

    recorder.record(event, context);

    assertThat(recordings).containsExactly(new Recording(primary, event, context));
  }

  @Test
  void theViewBoundToAContextRecordsAboutItsPrimaryResource() {
    var bound = recorder.forContext(context);

    bound.normal("Created", "created");
    bound.warn("Failed", "failed");
    bound.record(EventRecord.builder().reason("Noticed").build());

    assertThat(recordings).hasSize(3);
    assertThat(recordings).allSatisfy(r -> assertThat(r.regarding()).isSameAs(primary));
    assertThat(recordings).allSatisfy(r -> assertThat(r.context()).isSameAs(context));
    assertThat(recordings)
        .extracting(r -> r.event().type())
        .containsExactly(EventType.NORMAL, EventType.WARNING, EventType.NORMAL);
    assertThat(recordings)
        .extracting(r -> r.event().reason())
        .containsExactly("Created", "Failed", "Noticed");
  }

  @Test
  void theViewBoundToAnotherObjectRecordsAboutThatObjectFromTheContext() {
    var bound = recorder.forResource(other, context);

    bound.normal("Noticed", "noticed");
    bound.warn("Failed", "failed");

    assertThat(recordings).hasSize(2);
    assertThat(recordings).allSatisfy(r -> assertThat(r.regarding()).isSameAs(other));
    assertThat(recordings).allSatisfy(r -> assertThat(r.context()).isSameAs(context));
  }

  @Test
  void theContextViewGoesThroughTheTwoArgumentRecordAndTheObjectViewDoesNot() {
    var calls = new ArrayList<String>();
    var recorder =
        new EventRecorder() {
          @Override
          public void record(HasMetadata regarding, EventRecord event, Context<?> context) {
            calls.add("about " + regarding.getMetadata().getName());
          }

          @Override
          public void record(EventRecord event, Context<?> context) {
            calls.add("primary");
            EventRecorder.super.record(event, context);
          }
        };

    recorder.forContext(context).normal("Created", "created");
    recorder.forResource(other, context).normal("Noticed", "noticed");

    assertThat(calls).containsExactly("primary", "about primary", "about other");
  }

  @Test
  void viewsRejectMissingArguments() {
    assertThatNullPointerException().isThrownBy(() -> recorder.forContext(null));
    assertThatNullPointerException().isThrownBy(() -> recorder.forResource(null, context));
    assertThatNullPointerException().isThrownBy(() -> recorder.forResource(other, null));
    assertThatNullPointerException()
        .isThrownBy(() -> recorder.record(EventRecord.normal("Created", "created"), null));
  }

  private Context<?> context() {
    Context<?> context = mock(Context.class);
    when(context.getPrimaryResource()).thenAnswer(invocation -> primary);
    return context;
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
