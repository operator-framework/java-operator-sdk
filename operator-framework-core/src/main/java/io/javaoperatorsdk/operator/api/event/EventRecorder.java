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

import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

/**
 * Records Kubernetes events on behalf of a controller.
 *
 * <p>This is the unbound form of the API: an instance is shared by all the controllers of the
 * operator, and everything that varies between them - the object an event is about, the controller
 * the event is attributed to, and the configuration the event is assembled from - is passed per
 * call, as the object and the {@link Context} of the reconciliation recording the event.
 * Implementations are therefore expected to be stateless and thread safe, and only have to
 * implement {@link #record(HasMetadata, EventRecord, Context)}: the other methods are conveniences
 * that delegate to it. To record events through an implementation of your own, see {@link
 * io.javaoperatorsdk.operator.api.config.ConfigurationService#eventRecorder()}.
 *
 * <p>Within a reconciliation, prefer {@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#eventRecorder()}, which is already bound to
 * the primary resource, or {@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#eventRecorder(HasMetadata)} for an event about
 * another object.
 *
 * <p>Whether a failure to record an event fails the caller is up to the implementation, see {@link
 * DefaultEventRecorder} for the default.
 */
@Experimental(API_MIGHT_CHANGE)
@FunctionalInterface
public interface EventRecorder {

  /**
   * Records an event about the given object from the given reconciliation: the event is attributed
   * to the controller of the reconciliation and assembled from its configuration.
   *
   * @param regarding the object the event is about, referenced as the involved object of the
   *     resulting event; not necessarily the primary resource of the reconciliation
   * @param event the event to record
   * @param context the context of the reconciliation recording the event
   */
  void record(HasMetadata regarding, EventRecord event, Context<?> context);

  /**
   * Records an event about the primary resource of the given reconciliation.
   *
   * @param event the event to record
   * @param context the context of the reconciliation recording the event
   */
  default void record(EventRecord event, Context<?> context) {
    Objects.requireNonNull(context, "the context of the reconciliation must not be null");
    record(context.getPrimaryResource(), event, context);
  }

  /**
   * Returns a view of this recorder bound to the given object and reconciliation, so that neither
   * has to be passed for every event. Events recorded through the view go through {@link
   * #record(HasMetadata, EventRecord, Context)}.
   *
   * @param regarding the object subsequent events will be about
   * @param context the context of the reconciliation recording them
   * @return a recorder bound to {@code regarding} and {@code context}
   */
  default ResourceEventRecorder forResource(HasMetadata regarding, Context<?> context) {
    Objects.requireNonNull(regarding, "the object events are recorded about must not be null");
    Objects.requireNonNull(context, "the context events are recorded from must not be null");
    return new BoundResourceEventRecorder(event -> record(regarding, event, context));
  }

  /**
   * Returns a view of this recorder bound to the primary resource of the given reconciliation, so
   * that the context doesn't have to be passed for every event. Events recorded through the view go
   * through {@link #record(EventRecord, Context)}, so an implementation that overrides that method
   * sees them.
   *
   * @param context the context subsequent events will be recorded from
   * @return a recorder bound to {@code context}
   */
  default ResourceEventRecorder forContext(Context<?> context) {
    Objects.requireNonNull(context, "the context events will be recorded from must not be null");
    return new BoundResourceEventRecorder(event -> record(event, context));
  }
}
