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

import java.util.function.UnaryOperator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

public class ReconcileUtils {

  private ReconcileUtils() {}

  // todo move finalizers mtehods & deprecate
  // todo namespace handling
  // todo compare resource version if multiple event sources provide the same resource
  // todo for json patch make sense to retry for ?

  public static <R extends HasMetadata> R serverSideApply(
      Context<? extends HasMetadata> context, R resource) {
    return serverSideApply(context, resource, true);
  }

  public static <R extends HasMetadata> R serverSideApply(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context,
        resource,
        r ->
            context
                .getClient()
                .resource(r)
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()),
        filterEvent);
  }

  public static <R extends HasMetadata> R serverSideApplyStatus(
      Context<? extends HasMetadata> context, R resource) {
    return serverSideApplyStatus(context, resource, true);
  }

  public static <R extends HasMetadata> R serverSideApplyStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context,
        resource,
        r ->
            context
                .getClient()
                .resource(r)
                .subresource("status")
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()),
        filterEvent);
  }

  public static <P extends HasMetadata> P serverSideApplyPrimary(Context<P> context, P resource) {
    return serverSideApplyPrimary(context, resource, true);
  }

  public static <P extends HasMetadata> P serverSideApplyPrimary(
      Context<P> context, P resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r ->
            context
                .getClient()
                .resource(r)
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  public static <P extends HasMetadata> P serverSideApplyPrimaryStatus(
      Context<P> context, P resource) {
    return serverSideApplyPrimaryStatus(context, resource, true);
  }

  public static <P extends HasMetadata> P serverSideApplyPrimaryStatus(
      Context<P> context, P resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r ->
            context
                .getClient()
                .resource(r)
                .subresource("status")
                .patch(
                    new PatchContext.Builder()
                        .withForce(true)
                        .withFieldManager(context.getControllerConfiguration().fieldManager())
                        .withPatchType(PatchType.SERVER_SIDE_APPLY)
                        .build()),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  public static <R extends HasMetadata> R update(
      Context<? extends HasMetadata> context, R resource) {
    return update(context, resource, true);
  }

  public static <R extends HasMetadata> R update(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).update(), filterEvent);
  }

  public static <R extends HasMetadata> R updateStatus(
      Context<? extends HasMetadata> context, R resource) {
    return updateStatus(context, resource, true);
  }

  public static <R extends HasMetadata> R updateStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).updateStatus(), filterEvent);
  }

  public static <R extends HasMetadata> R updatePrimary(
      Context<? extends HasMetadata> context, R resource) {
    return updatePrimary(context, resource, true);
  }

  public static <R extends HasMetadata> R updatePrimary(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).update(),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  public static <R extends HasMetadata> R updatePrimaryStatus(
      Context<? extends HasMetadata> context, R resource) {
    return updatePrimaryStatus(context, resource, true);
  }

  public static <R extends HasMetadata> R updatePrimaryStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).updateStatus(),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  public static <R extends HasMetadata> R jsonPatch(
      Context<? extends HasMetadata> context, R resource, UnaryOperator<R> unaryOperator) {
    return jsonPatch(context, resource, unaryOperator, true);
  }

  public static <R extends HasMetadata> R jsonPatch(
      Context<? extends HasMetadata> context,
      R resource,
      UnaryOperator<R> unaryOperator,
      boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).edit(unaryOperator), filterEvent);
  }

  public static <R extends HasMetadata> R jsonPatchStatus(
      Context<? extends HasMetadata> context, R resource, UnaryOperator<R> unaryOperator) {
    return jsonPatchStatus(context, resource, unaryOperator, true);
  }

  public static <R extends HasMetadata> R jsonPatchStatus(
      Context<? extends HasMetadata> context,
      R resource,
      UnaryOperator<R> unaryOperator,
      boolean filterEvent) {
    return resourcePatch(
        context,
        resource,
        r -> context.getClient().resource(r).editStatus(unaryOperator),
        filterEvent);
  }

  public static <R extends HasMetadata> R jsonPatchPrimary(
      Context<? extends HasMetadata> context, R resource, UnaryOperator<R> unaryOperator) {
    return jsonPatchPrimary(context, resource, unaryOperator, true);
  }

  public static <R extends HasMetadata> R jsonPatchPrimary(
      Context<? extends HasMetadata> context,
      R resource,
      UnaryOperator<R> unaryOperator,
      boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).edit(unaryOperator),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  public static <R extends HasMetadata> R jsonPatchPrimaryStatus(
      Context<? extends HasMetadata> context, R resource, UnaryOperator<R> unaryOperator) {
    return jsonPatchPrimaryStatus(context, resource, unaryOperator, true);
  }

  public static <R extends HasMetadata> R jsonPatchPrimaryStatus(
      Context<? extends HasMetadata> context,
      R resource,
      UnaryOperator<R> unaryOperator,
      boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).editStatus(unaryOperator),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  public static <R extends HasMetadata> R jsonMergePatch(
      Context<? extends HasMetadata> context, R resource) {
    return jsonMergePatch(context, resource, true);
  }

  public static <R extends HasMetadata> R jsonMergePatch(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).patch(), filterEvent);
  }

  public static <R extends HasMetadata> R jsonMergePatchStatus(
      Context<? extends HasMetadata> context, R resource) {
    return jsonMergePatchStatus(context, resource, true);
  }

  public static <R extends HasMetadata> R jsonMergePatchStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        context, resource, r -> context.getClient().resource(r).patchStatus(), filterEvent);
  }

  public static <R extends HasMetadata> R jsonMergePatchPrimary(
      Context<? extends HasMetadata> context, R resource) {
    return jsonMergePatchPrimary(context, resource, true);
  }

  public static <R extends HasMetadata> R jsonMergePatchPrimary(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patch(),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  public static <R extends HasMetadata> R jsonMergePatchPrimaryStatus(
      Context<? extends HasMetadata> context, R resource) {
    return jsonMergePatchPrimaryStatus(context, resource, true);
  }

  public static <R extends HasMetadata> R jsonMergePatchPrimaryStatus(
      Context<? extends HasMetadata> context, R resource, boolean filterEvent) {
    return resourcePatch(
        resource,
        r -> context.getClient().resource(r).patch(),
        context.eventSourceRetriever().getControllerEventSource(),
        filterEvent);
  }

  public static <R extends HasMetadata> R resourcePatch(
      Context<?> context, R resource, UnaryOperator<R> updateOperation, boolean filterEvent) {

    var esList = context.eventSourceRetriever().getEventSourcesFor(resource.getClass());
    if (esList.isEmpty()) {
      throw new IllegalStateException("No event source found for type: " + resource.getClass());
    }
    if (esList.size() > 1) {
      throw new IllegalStateException(
          "Multiple event sources found for: "
              + resource.getClass()
              + " please provide the target event source");
    }
    var es = esList.get(0);
    if (es instanceof ManagedInformerEventSource mes) {
      return resourcePatch(resource, updateOperation, mes, filterEvent);
    } else {
      throw new IllegalStateException(
          "Target event source must be a subclass off "
              + ManagedInformerEventSource.class.getName());
    }
  }

  @SuppressWarnings("unchecked")
  public static <R extends HasMetadata> R resourcePatch(
      R resource,
      UnaryOperator<R> updateOperation,
      ManagedInformerEventSource ies,
      boolean filterEvent) {
    return filterEvent
        ? (R) ies.eventFilteringUpdateAndCacheResource(resource, updateOperation)
        : (R) ies.updateAndCacheResource(resource, updateOperation);
  }
}
