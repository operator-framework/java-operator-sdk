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
package io.javaoperatorsdk.operator.baseapi.cachingfilteringupdate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class CachingFilteringUpdateReconciler
    implements Reconciler<CachingFilteringUpdateCustomResource> {

  private final AtomicBoolean issueFound = new AtomicBoolean(false);

  @Override
  public UpdateControl<CachingFilteringUpdateCustomResource> reconcile(
      CachingFilteringUpdateCustomResource resource,
      Context<CachingFilteringUpdateCustomResource> context) {

    context.resourceOperations().serverSideApply(prepareCM(resource));
    var cachedCM = context.getSecondaryResource(ConfigMap.class);
    if (cachedCM.isEmpty()) {
      issueFound.set(true);
      throw new IllegalStateException("Error for resource: " + ResourceID.fromResource(resource));
    }

    ensureStatusExists(resource);
    resource.getStatus().setUpdated(true);
    return UpdateControl.patchStatus(resource);
  }

  private static ConfigMap prepareCM(CachingFilteringUpdateCustomResource p) {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(p.getMetadata().getName())
                    .withNamespace(p.getMetadata().getNamespace())
                    .build())
            .withData(Map.of("name", p.getMetadata().getName()))
            .build();
    cm.addOwnerReference(p);
    return cm;
  }

  @Override
  public List<EventSource<?, CachingFilteringUpdateCustomResource>> prepareEventSources(
      EventSourceContext<CachingFilteringUpdateCustomResource> context) {
    InformerEventSource<ConfigMap, CachingFilteringUpdateCustomResource> cmES =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, CachingFilteringUpdateCustomResource.class)
                .build(),
            context);
    return List.of(cmES);
  }

  private void ensureStatusExists(CachingFilteringUpdateCustomResource resource) {
    CachingFilteringUpdateStatus status = resource.getStatus();
    if (status == null) {
      status = new CachingFilteringUpdateStatus();
      resource.setStatus(status);
    }
  }

  public boolean isIssueFound() {
    return issueFound.get();
  }
}
