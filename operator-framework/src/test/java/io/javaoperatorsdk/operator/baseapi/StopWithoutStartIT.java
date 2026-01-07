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
package io.javaoperatorsdk.operator.baseapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@Sample(
    tldr = "Stop Operator Without Starting",
    description =
        """
        Demonstrates that an operator can be stopped without being started. This is important \
        for cleanup scenarios where an operator is created and registered with reconcilers but \
        never started due to initialization failures or other conditions. The stop() method \
        properly cleans up thread pools even when the operator was never started.
        """)
class StopWithoutStartIT {

  @Test
  @Timeout(5)
  void stopWithoutStartShouldNotThrowException() {
    Operator operator = new Operator();
    operator.register(new DummyReconciler());
    // Call stop without start - should clean up thread pools without throwing exception
    operator.stop();
  }

  @ControllerConfiguration
  public static class DummyReconciler implements Reconciler<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return UpdateControl.noUpdate();
    }
  }
}
