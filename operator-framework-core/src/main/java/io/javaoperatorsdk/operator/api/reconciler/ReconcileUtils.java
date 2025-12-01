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

import java.lang.reflect.InvocationTargetException;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

public class ReconcileUtils {

  private static final Logger log = LoggerFactory.getLogger(ReconcileUtils.class);

  public static final int DEFAULT_MAX_RETRY = 10;

  private ReconcileUtils() {}

  // todo move finalizers mtehods & deprecate
  // todo test namespace handling
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

  public static <P extends HasMetadata> P addFinalizer(Context<P> context) {
    return addFinalizer(context, context.getControllerConfiguration().getFinalizerName());
  }

  /**
   * Adds finalizer to the primary resource from the context using JSON Patch. Retries conflicts and
   * unprocessable content (HTTP 422), see {@link
   * PrimaryUpdateAndCacheUtils#conflictRetryingPatch(KubernetesClient, HasMetadata, UnaryOperator,
   * Predicate)} for details on retry. It does not add finalizer if there is already a finalizer or
   * resource is marked for deletion.
   *
   * @return updated resource from the server response
   */
  public static <P extends HasMetadata> P addFinalizer(Context<P> context, String finalizer) {
    return addFinalizer(context.getClient(), context.getPrimaryResource(), finalizer);
  }

  /**
   * Adds finalizer to the resource using JSON Patch. Retries conflicts and unprocessable content
   * (HTTP 422), see {@link PrimaryUpdateAndCacheUtils#conflictRetryingPatch(KubernetesClient,
   * HasMetadata, UnaryOperator, Predicate)} for details on retry. It does not try to add finalizer
   * if there is already a finalizer or resource is marked for deletion.
   *
   * @return updated resource from the server response
   */
  public static <P extends HasMetadata> P addFinalizer(
      KubernetesClient client, P resource, String finalizerName) {
    if (resource.isMarkedForDeletion() || resource.hasFinalizer(finalizerName)) {
      return resource;
    }
    return conflictRetryingPatch(
        client,
        resource,
        r -> {
          r.addFinalizer(finalizerName);
          return r;
        },
        r -> !r.hasFinalizer(finalizerName));
  }

  public static <P extends HasMetadata> P removeFinalizer(Context<P> context) {
    return removeFinalizer(context, context.getControllerConfiguration().getFinalizerName());
  }

  /**
   * Removes the target finalizer from the primary resource from the Context. Uses JSON Patch and
   * handles retries, see {@link PrimaryUpdateAndCacheUtils#conflictRetryingPatch(KubernetesClient,
   * HasMetadata, UnaryOperator, Predicate)} for details. It does not try to remove finalizer if
   * finalizer is not present on the resource.
   *
   * @return updated resource from the server response
   */
  public static <P extends HasMetadata> P removeFinalizer(
      Context<P> context, String finalizerName) {
    return removeFinalizer(context.getClient(), context.getPrimaryResource(), finalizerName);
  }

  /**
   * Removes the target finalizer from target resource. Uses JSON Patch and handles retries, see
   * {@link PrimaryUpdateAndCacheUtils#conflictRetryingPatch(KubernetesClient, HasMetadata,
   * UnaryOperator, Predicate)} for details. It does not try to remove finalizer if finalizer is not
   * present on the resource.
   *
   * @return updated resource from the server response
   */
  public static <P extends HasMetadata> P removeFinalizer(
      KubernetesClient client, P resource, String finalizerName) {
    if (!resource.hasFinalizer(finalizerName)) {
      return resource;
    }
    return conflictRetryingPatch(
        client,
        resource,
        r -> {
          r.removeFinalizer(finalizerName);
          return r;
        },
        r -> r.hasFinalizer(finalizerName));
  }

  /**
   * Patches the resource using JSON Patch. In case the server responds with conflict (HTTP 409) or
   * unprocessable content (HTTP 422) it retries the operation up to the maximum number defined in
   * {@link PrimaryUpdateAndCacheUtils#DEFAULT_MAX_RETRY}.
   *
   * @param client KubernetesClient
   * @param resource to update
   * @param resourceChangesOperator changes to be done on the resource before update
   * @param preCondition condition to check if the patch operation still needs to be performed or
   *     not.
   * @return updated resource from the server or unchanged if the precondition does not hold.
   * @param <P> resource type
   */
  @SuppressWarnings("unchecked")
  public static <P extends HasMetadata> P conflictRetryingPatch(
      KubernetesClient client,
      P resource,
      UnaryOperator<P> resourceChangesOperator,
      Predicate<P> preCondition) {
    if (log.isDebugEnabled()) {
      log.debug("Conflict retrying update for: {}", ResourceID.fromResource(resource));
    }
    int retryIndex = 0;
    while (true) {
      try {
        if (!preCondition.test(resource)) {
          return resource;
        }
        return client.resource(resource).edit(resourceChangesOperator);
      } catch (KubernetesClientException e) {
        log.trace("Exception during patch for resource: {}", resource);
        retryIndex++;
        // only retry on conflict (409) and unprocessable content (422) which
        // can happen if JSON Patch is not a valid request since there was
        // a concurrent request which already removed another finalizer:
        // List element removal from a list is by index in JSON Patch
        // so if addressing a second finalizer but first is meanwhile removed
        // it is a wrong request.
        if (e.getCode() != 409 && e.getCode() != 422) {
          throw e;
        }
        if (retryIndex >= DEFAULT_MAX_RETRY) {
          throw new OperatorException(
              "Exceeded maximum ("
                  + DEFAULT_MAX_RETRY
                  + ") retry attempts to patch resource: "
                  + ResourceID.fromResource(resource));
        }
        log.debug(
            "Retrying patch for resource name: {}, namespace: {}; HTTP code: {}",
            resource.getMetadata().getName(),
            resource.getMetadata().getNamespace(),
            e.getCode());
        var operation = client.resources(resource.getClass());
        if (resource.getMetadata().getNamespace() != null) {
          resource =
              (P)
                  operation
                      .inNamespace(resource.getMetadata().getNamespace())
                      .withName(resource.getMetadata().getName())
                      .get();
        } else {
          resource = (P) operation.withName(resource.getMetadata().getName()).get();
        }
      }
    }
  }

  public static <P extends HasMetadata> P addFinalizerWithSSA(Context<P> context) {
    return addFinalizerWithSSA(context, context.getControllerConfiguration().getFinalizerName());
  }

  /**
   * Adds finalizer using Server-Side Apply. In the background this method creates a fresh copy of
   * the target resource, setting only name, namespace and finalizer. Does not use optimistic
   * locking for the patch.
   *
   * @return the patched resource from the server response
   */
  public static <P extends HasMetadata> P addFinalizerWithSSA(
      Context<P> context, String finalizerName) {
    var originalResource = context.getPrimaryResource();
    if (log.isDebugEnabled()) {
      log.debug(
          "Adding finalizer (using SSA) for resource: {} version: {}",
          getUID(originalResource),
          getVersion(originalResource));
    }
    try {
      P resource = (P) originalResource.getClass().getConstructor().newInstance();
      ObjectMeta objectMeta = new ObjectMeta();
      objectMeta.setName(originalResource.getMetadata().getName());
      objectMeta.setNamespace(originalResource.getMetadata().getNamespace());
      resource.setMetadata(objectMeta);
      resource.addFinalizer(finalizerName);

      return serverSideApplyPrimary(context, resource);
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new RuntimeException(
          "Issue with creating custom resource instance with reflection."
              + " Custom Resources must provide a no-arg constructor. Class: "
              + originalResource.getClass().getName(),
          e);
    }
  }

  public static int compareResourceVersions(HasMetadata h1, HasMetadata h2) {
    return compareResourceVersions(
        h1.getMetadata().getResourceVersion(), h2.getMetadata().getResourceVersion());
  }

  public static int compareResourceVersions(String v1, String v2) {
    int v1Length = validateResourceVersion(v1);
    int v2Length = validateResourceVersion(v2);
    int comparison = v1Length - v2Length;
    if (comparison != 0) {
      return comparison;
    }
    for (int i = 0; i < v2Length; i++) {
      int comp = v1.charAt(i) - v2.charAt(i);
      if (comp != 0) {
        return comp;
      }
    }
    return 0;
  }

  private static int validateResourceVersion(String v1) {
    int v1Length = v1.length();
    if (v1Length == 0) {
      throw new NonComparableResourceVersionException("Resource version is empty");
    }
    for (int i = 0; i < v1Length; i++) {
      char char1 = v1.charAt(i);
      if (char1 == '0') {
        if (i == 0) {
          throw new NonComparableResourceVersionException(
              "Resource version cannot begin with 0: " + v1);
        }
      } else if (char1 < '0' || char1 > '9') {
        throw new NonComparableResourceVersionException(
            "Non numeric characters in resource version: " + v1);
      }
    }
    return v1Length;
  }
}
