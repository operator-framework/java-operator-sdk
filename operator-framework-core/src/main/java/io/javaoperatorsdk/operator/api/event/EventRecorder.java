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

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

/**
 * Records Kubernetes events on behalf of a controller.
 *
 * <p>This is the unbound form of the API: an instance is shared by all the controllers of the
 * operator, and everything that varies between them - the primary resource an event is about, the
 * controller the event is attributed to, and the configuration the event is assembled from - is
 * passed per call, as the {@link Context} of the reconciliation recording the event.
 * Implementations are therefore expected to be stateless and thread safe. To record events through
 * an implementation of your own, see {@link
 * io.javaoperatorsdk.operator.api.config.ConfigurationService#eventRecorder()}. Within a
 * reconciliation, prefer {@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#eventRecorder()}, which is already bound to
 * the context.
 *
 * <p>Recording an event is best effort: failures to write the event to the cluster are logged and
 * swallowed, and never fail the caller.
 */
@Experimental(API_MIGHT_CHANGE)
public interface EventRecorder {

  /**
   * Records an event about the primary resource of the given reconciliation.
   *
   * @param event the event to record
   * @param context the context of the reconciliation recording the event; the event is about its
   *     primary resource and is attributed to its controller
   */
  void record(EventRecord event, Context<?> context);

  /**
   * Returns a view of this recorder bound to the given reconciliation, so that the context doesn't
   * have to be passed for every event.
   *
   * @param context the context subsequent events will be recorded from
   * @return a recorder bound to {@code context}
   */
  ResourceEventRecorder forContext(Context<?> context);
}
