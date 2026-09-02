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

import io.fabric8.kubernetes.api.model.Event;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

/**
 * Writes fully built events somewhere. Extracted from {@link EventRecorder} so that the assembly of
 * the event and its delivery can be tested and replaced independently. Note that events are
 * deliberately not written through {@link
 * io.javaoperatorsdk.operator.api.reconciler.ResourceOperations}: nothing observes events through
 * an informer, so there is no cache to keep primed.
 */
public interface EventSink {

  /**
   * Delivers the event.
   *
   * @param event the event to deliver, fully assembled: everything the event says is already built
   *     into it
   * @param context the context of the reconciliation the event was recorded from, for
   *     implementations that route the event based on it rather than on its contents. Ignored by
   *     {@link DefaultEventSink}.
   */
  @Experimental(API_MIGHT_CHANGE)
  void emit(Event event, Context<?> context);
}
