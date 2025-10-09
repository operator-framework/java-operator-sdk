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
package io.javaoperatorsdk.operator.baseapi.builtinresourcecleaner;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.Service;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration(informer = @Informer(labelSelector = "builtintest=true"))
public class BuiltInResourceCleanerReconciler implements Reconciler<Service>, Cleaner<Service> {

  private final AtomicInteger reconciled = new AtomicInteger(0);
  private final AtomicInteger cleaned = new AtomicInteger(0);

  @Override
  public UpdateControl<Service> reconcile(Service resource, Context<Service> context) {
    reconciled.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(Service resource, Context<Service> context) {
    cleaned.addAndGet(1);
    return DeleteControl.defaultDelete();
  }

  public int getReconcileCount() {
    return reconciled.get();
  }

  public int getCleanCount() {
    return cleaned.get();
  }
}
