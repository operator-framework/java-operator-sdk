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
package io.javaoperatorsdk.operator.api.reconciler;

import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public interface Reconciler<P extends HasMetadata> {

  /**
   * The implementation of this operation is required to be idempotent. Always use the UpdateControl
   * object to make updates on custom resource if possible.
   *
   * @throws Exception from the custom implementation
   * @param resource the resource that has been created or updated
   * @param context the context with which the operation is executed
   * @return UpdateControl to manage updates on the custom resource (usually the status) after
   *     reconciliation.
   */
  UpdateControl<P> reconcile(P resource, Context<P> context) throws Exception;

  /**
   * Prepares a map of {@link EventSource} implementations keyed by the name with which they need to
   * be registered by the SDK.
   *
   * @param context a {@link EventSourceContext} providing access to information useful to event
   *     sources
   * @return a list of event sources
   */
  default List<EventSource<?, P>> prepareEventSources(EventSourceContext<P> context) {
    return Collections.emptyList();
  }

  /**
   * Reconciler can override this method in order to update the status sub-resource in the case an
   * exception in thrown. In that case {@link #updateErrorStatus(HasMetadata, Context, Exception)}
   * is called automatically.
   *
   * <p>The result of the method call is used to make a status update on the custom resource. This
   * is always a sub-resource update request, so no update on custom resource itself (like spec of
   * metadata) happens. Note that this update request will also produce an event, and will result in
   * a reconciliation if the controller is not generation aware.
   *
   * <p>Note that the scope of this feature is only the reconcile method of the reconciler, since
   * there should not be updates on custom resource after it is marked for deletion.
   *
   * @param resource to update the status on
   * @param context the current context
   * @param e exception thrown from the reconciler
   * @return the updated resource
   */
  default ErrorStatusUpdateControl<P> updateErrorStatus(
      P resource, Context<P> context, Exception e) {
    return ErrorStatusUpdateControl.defaultErrorProcessing();
  }
}
