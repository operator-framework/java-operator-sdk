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

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

/**
 * Derives the default aggregation key of an event, used when the {@link EventRecord} does not set
 * one explicitly. The key identifies an event among the events about the same object, so that
 * repeated occurrences resolve to the same event rather than to one event each, see {@link
 * EventRecord#key()}.
 *
 * <p>An empty result leaves the record without a default key, which keeps the message part of the
 * event identity.
 *
 * <p>Implementations are called from concurrent reconciliations and must be thread safe.
 */
@Experimental(API_MIGHT_CHANGE)
@FunctionalInterface
public interface EventKeyStrategy {

  Optional<String> keyFor(HasMetadata regarding, EventRecord record);

  /** No default key: the message stays part of the event identity. */
  static EventKeyStrategy none() {
    return (regarding, record) -> Optional.empty();
  }

  /**
   * Aggregates by event type and reason: all occurrences of a reason resolve to one event whose
   * count grows and whose message is replaced with the latest one. The right choice for events that
   * report a current state rather than individual occurrences.
   */
  static EventKeyStrategy byReason() {
    return (regarding, record) -> Optional.of(record.type().value() + "/" + record.reason());
  }
}
