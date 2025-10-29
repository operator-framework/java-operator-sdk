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
package io.javaoperatorsdk.operator.baseapi.dynamicgenericeventsourceregistration;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class DynamicGenericEventSourceRegistrationReconciler
    implements Reconciler<DynamicGenericEventSourceRegistrationCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final AtomicInteger numberOfEventSources = new AtomicInteger();

  @Override
  public UpdateControl<DynamicGenericEventSourceRegistrationCustomResource> reconcile(
      DynamicGenericEventSourceRegistrationCustomResource primary,
      Context<DynamicGenericEventSourceRegistrationCustomResource> context) {

    numberOfExecutions.addAndGet(1);

    context
        .eventSourceRetriever()
        .dynamicallyRegisterEventSource(genericInformerFor(ConfigMap.class, context));
    context
        .eventSourceRetriever()
        .dynamicallyRegisterEventSource(genericInformerFor(Secret.class, context));

    context.getClient().resource(secret(primary)).createOr(NonDeletingOperation::update);
    context.getClient().resource(configMap(primary)).createOr(NonDeletingOperation::update);

    numberOfEventSources.set(
        context.eventSourceRetriever().getEventSourcesFor(GenericKubernetesResource.class).size());

    return UpdateControl.noUpdate();
  }

  private Secret secret(DynamicGenericEventSourceRegistrationCustomResource primary) {
    var secret =
        new SecretBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(primary.getMetadata().getName())
                    .withNamespace(primary.getMetadata().getNamespace())
                    .build())
            .withData(Map.of("key", Base64.getEncoder().encodeToString("val".getBytes())))
            .build();
    secret.addOwnerReference(primary);
    return secret;
  }

  private ConfigMap configMap(DynamicGenericEventSourceRegistrationCustomResource primary) {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(primary.getMetadata().getName())
                    .withNamespace(primary.getMetadata().getNamespace())
                    .build())
            .withData(Map.of("key", "val"))
            .build();
    cm.addOwnerReference(primary);
    return cm;
  }

  private InformerEventSource<
          GenericKubernetesResource, DynamicGenericEventSourceRegistrationCustomResource>
      genericInformerFor(
          Class<? extends HasMetadata> clazz,
          Context<DynamicGenericEventSourceRegistrationCustomResource> context) {

    return new InformerEventSource<>(
        InformerEventSourceConfiguration.from(
                GroupVersionKind.gvkFor(clazz),
                DynamicGenericEventSourceRegistrationCustomResource.class)
            .withName(clazz.getSimpleName())
            .build(),
        context.eventSourceRetriever().eventSourceContextForDynamicRegistration());
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public int getNumberOfEventSources() {
    return numberOfEventSources.get();
  }
}
