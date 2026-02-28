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
package io.javaoperatorsdk.operator.baseapi.configloader;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

/**
 * A reconciler that fails for the first {@code numberOfFailures} invocations and then succeeds,
 * setting the status to {@link ConfigLoaderTestCustomResourceStatus.State#SUCCESS}.
 */
@ControllerConfiguration
public class ConfigLoaderTestReconciler
    implements Reconciler<ConfigLoaderTestCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final int numberOfFailures;

  public ConfigLoaderTestReconciler(int numberOfFailures) {
    this.numberOfFailures = numberOfFailures;
  }

  @Override
  public UpdateControl<ConfigLoaderTestCustomResource> reconcile(
      ConfigLoaderTestCustomResource resource, Context<ConfigLoaderTestCustomResource> context) {
    int execution = numberOfExecutions.incrementAndGet();
    if (execution <= numberOfFailures) {
      throw new RuntimeException("Simulated failure on execution " + execution);
    }
    var status = new ConfigLoaderTestCustomResourceStatus();
    status.setState(ConfigLoaderTestCustomResourceStatus.State.SUCCESS);
    resource.setStatus(status);
    return UpdateControl.patchStatus(resource);
  }

  @Override
  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
