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
package io.javaoperatorsdk.operator.baseapi.fieldselector;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.config.informer.Field;
import io.javaoperatorsdk.operator.api.config.informer.FieldSelectorBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(
    informer =
        @Informer(
            fieldSelector =
                @Field(path = "type", value = FieldSelectorTestReconciler.MY_SECRET_TYPE)))
public class FieldSelectorTestReconciler implements Reconciler<Secret>, TestExecutionInfoProvider {

  public static final String MY_SECRET_TYPE = "my-secret-type";
  public static final String OTHER_SECRET_TYPE = "my-dependent-secret-type";
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private Set<String> reconciledSecrets = Collections.synchronizedSet(new HashSet<>());
  private InformerEventSource<Secret, Secret> dependentSecretEventSource;

  @Override
  public UpdateControl<Secret> reconcile(Secret resource, Context<Secret> context) {
    reconciledSecrets.add(resource.getMetadata().getName());
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public Set<String> getReconciledSecrets() {
    return reconciledSecrets;
  }

  @Override
  public List<EventSource<?, Secret>> prepareEventSources(EventSourceContext<Secret> context) {
    dependentSecretEventSource =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(Secret.class, Secret.class)
                .withNamespacesInheritedFromController()
                .withFieldSelector(
                    new FieldSelectorBuilder().withField("type", OTHER_SECRET_TYPE).build())
                .build(),
            context);

    return List.of(dependentSecretEventSource);
  }

  public InformerEventSource<Secret, Secret> getDependentSecretEventSource() {
    return dependentSecretEventSource;
  }
}
