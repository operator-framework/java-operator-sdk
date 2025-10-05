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
package io.javaoperatorsdk.operator.baseapi.labelselector;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

import static io.javaoperatorsdk.operator.baseapi.labelselector.LabelSelectorTestReconciler.LABEL_KEY;
import static io.javaoperatorsdk.operator.baseapi.labelselector.LabelSelectorTestReconciler.LABEL_VALUE;

@ControllerConfiguration(informer = @Informer(labelSelector = LABEL_KEY + "=" + LABEL_VALUE))
public class LabelSelectorTestReconciler
    implements Reconciler<LabelSelectorTestCustomResource>, TestExecutionInfoProvider {

  public static final String LABEL_KEY = "app";
  public static final String LABEL_VALUE = "myapp";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<LabelSelectorTestCustomResource> reconcile(
      LabelSelectorTestCustomResource resource, Context<LabelSelectorTestCustomResource> context) {

    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
