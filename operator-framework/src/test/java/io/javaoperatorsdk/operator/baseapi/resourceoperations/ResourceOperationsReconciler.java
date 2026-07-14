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
package io.javaoperatorsdk.operator.baseapi.resourceoperations;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * Exercises every primary update/patch operation exposed by {@link
 * io.javaoperatorsdk.operator.api.reconciler.ResourceOperations}. On each reconciliation it applies
 * the change dictated by the currently selected {@link Operation} using {@code
 * context.resourceOperations()} and then returns {@link UpdateControl#noUpdate()} (the write was
 * already performed via the operations helper).
 *
 * <p>Status operations write a fixed value into the status subresource; whole-resource operations
 * write a fixed value into the spec. Every operation uses its default matcher, so once the change
 * is present on the server subsequent reconciliations must match and become no-ops - which,
 * together with own event filtering, is what keeps the controller from looping on its own writes.
 */
@ControllerConfiguration
public class ResourceOperationsReconciler implements Reconciler<ResourceOperationsCustomResource> {

  public static final String STATUS_VALUE = "reconciled";
  public static final String SPEC_MARKER = "applied";

  public enum Operation {
    SSA,
    SSA_STATUS,
    UPDATE,
    UPDATE_STATUS,
    JSON_PATCH,
    JSON_PATCH_STATUS,
    JSON_MERGE_PATCH,
    JSON_MERGE_PATCH_STATUS
  }

  private volatile Operation operation;
  final AtomicInteger numberOfExecutions = new AtomicInteger();

  @Override
  public UpdateControl<ResourceOperationsCustomResource> reconcile(
      ResourceOperationsCustomResource resource,
      Context<ResourceOperationsCustomResource> context) {
    numberOfExecutions.incrementAndGet();
    var ops = context.resourceOperations();

    switch (operation) {
      case SSA -> {
        markResource(resource);
        // SSA does not use optimistic locking
        resource.getMetadata().setManagedFields(null);
        resource.getMetadata().setResourceVersion(null);
        ops.serverSideApplyPrimary(resource);
      }
      case SSA_STATUS -> {
        ResourceOperationsCustomResource fresh = new ResourceOperationsCustomResource();
        fresh.setMetadata(
            new ObjectMetaBuilder()
                .withName(resource.getMetadata().getName())
                .withNamespace(resource.getMetadata().getNamespace())
                .build());

        fresh.setStatus(new ResourceOperationsStatus().setValue(STATUS_VALUE));
        fresh.getMetadata().setResourceVersion(null);
        ops.serverSideApplyPrimaryStatus(fresh);
      }
      case UPDATE -> {
        markResource(resource);
        ops.updatePrimary(resource);
      }
      case UPDATE_STATUS -> {
        resource.setStatus(new ResourceOperationsStatus().setValue(STATUS_VALUE));
        ops.updatePrimaryStatus(resource);
      }
      case JSON_PATCH -> ops.jsonPatchPrimary(resource, ResourceOperationsReconciler::markResource);
      case JSON_PATCH_STATUS ->
          ops.jsonPatchPrimaryStatus(
              resource,
              r -> {
                r.setStatus(new ResourceOperationsStatus().setValue(STATUS_VALUE));
                return r;
              });
      case JSON_MERGE_PATCH -> {
        markResource(resource);
        ops.jsonMergePatchPrimary(resource);
      }
      case JSON_MERGE_PATCH_STATUS -> {
        resource.setStatus(new ResourceOperationsStatus().setValue(STATUS_VALUE));
        ops.jsonMergePatchPrimaryStatus(resource);
      }
    }
    return UpdateControl.noUpdate();
  }

  private static ResourceOperationsCustomResource markResource(
      ResourceOperationsCustomResource resource) {
    resource.getSpec().setValue(SPEC_MARKER);
    return resource;
  }

  public void setOperation(Operation operation) {
    this.operation = operation;
  }
}
