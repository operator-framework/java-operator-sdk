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
package io.javaoperatorsdk.operator.api.reconciler.dependent.managed;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowCleanupResult;
import io.javaoperatorsdk.operator.processing.dependent.workflow.WorkflowReconcileResult;

@SuppressWarnings("rawtypes")
public class DefaultManagedWorkflowAndDependentResourceContext<P extends HasMetadata>
    implements ManagedWorkflowAndDependentResourceContext {
  private static final Logger log =
      LoggerFactory.getLogger(DefaultManagedWorkflowAndDependentResourceContext.class);
  public static final Object RECONCILE_RESULT_KEY = new Object();
  public static final Object CLEANUP_RESULT_KEY = new Object();
  private final ConcurrentHashMap attributes = new ConcurrentHashMap();
  private final Controller<P> controller;
  private final P primaryResource;
  private final Context<P> context;

  public DefaultManagedWorkflowAndDependentResourceContext(
      Controller<P> controller, P primaryResource, Context<P> context) {
    this.controller = controller;
    this.primaryResource = primaryResource;
    this.context = context;
  }

  @Override
  public <T> Optional<T> get(Object key, Class<T> expectedType) {
    return Optional.ofNullable(attributes.get(key))
        .filter(expectedType::isInstance)
        .map(expectedType::cast);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T put(Object key, T value) {
    Object previous;
    if (value == null) {
      return (T) attributes.remove(key);
    } else {
      previous = attributes.put(key, value);
    }

    if (previous != null && !previous.getClass().isAssignableFrom(value.getClass())) {
      logWarning(
          "Previous value ("
              + previous
              + ") for key ("
              + key
              + ") was not of type "
              + value.getClass()
              + ". This might indicate an issue in your code. If not, use put("
              + key
              + ", null) first to remove the previous value.");
    }
    return (T) previous;
  }

  // only for testing purposes
  void logWarning(String message) {
    log.warn(message);
  }

  @Override
  @SuppressWarnings("unused")
  public <T> T getMandatory(Object key, Class<T> expectedType) {
    return get(key, expectedType)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Mandatory attribute (key: "
                        + key
                        + ", type: "
                        + expectedType.getName()
                        + ") is missing or not of the expected type"));
  }

  @Override
  public Optional<WorkflowReconcileResult> getWorkflowReconcileResult() {
    return get(RECONCILE_RESULT_KEY, WorkflowReconcileResult.class);
  }

  @Override
  public Optional<WorkflowCleanupResult> getWorkflowCleanupResult() {
    return get(CLEANUP_RESULT_KEY, WorkflowCleanupResult.class);
  }

  @Override
  public WorkflowReconcileResult reconcileManagedWorkflow() {
    if (!controller.isWorkflowExplicitInvocation()) {
      throw new IllegalStateException("Workflow explicit invocation is not set.");
    }
    return controller.reconcileManagedWorkflow(primaryResource, context);
  }

  @Override
  public WorkflowCleanupResult cleanupManageWorkflow() {
    if (!controller.isWorkflowExplicitInvocation()) {
      throw new IllegalStateException("Workflow explicit invocation is not set.");
    }
    return controller.cleanupManagedWorkflow(primaryResource, context);
  }
}
