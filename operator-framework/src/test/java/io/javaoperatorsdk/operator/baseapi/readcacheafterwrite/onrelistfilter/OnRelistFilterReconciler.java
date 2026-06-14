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
package io.javaoperatorsdk.operator.baseapi.readcacheafterwrite.onrelistfilter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class OnRelistFilterReconciler implements Reconciler<OnRelistFilterCustomResource> {

  public enum Mode {
    /** No re-list around the update — the resulting watch event must be filtered as own write. */
    NO_RELIST,
    /**
     * onBeforeList → SSA → onList. The whole own-write window happens during a re-list. The watch
     * event must be propagated, since intermediate events may have been lost.
     */
    RELIST_AROUND_UPDATE,
    /**
     * onBeforeList → onList → SSA. The re-list completed cleanly before our update started, so the
     * resulting watch event must be filtered as own write.
     */
    RELIST_COMPLETES_BEFORE_UPDATE,
    /**
     * Update window opens → onBeforeList → SSA → onList. The re-list starts while our update is
     * already in progress; the existing event-filter window must flip into "do not absorb own
     * writes" mode and propagate the watch event.
     */
    RELIST_STARTS_DURING_UPDATE
  }

  static final String CM_DATA_KEY = "iteration";

  private final AtomicInteger numberOfExecutions = new AtomicInteger();
  private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.NO_RELIST);

  private RelistAwareInformerEventSource<ConfigMap, OnRelistFilterCustomResource>
      configMapEventSource;

  @Override
  public UpdateControl<OnRelistFilterCustomResource> reconcile(
      OnRelistFilterCustomResource resource, Context<OnRelistFilterCustomResource> context) {
    int execution = numberOfExecutions.incrementAndGet();

    if (execution == 1) {
      var cm = prepareConfigMap(resource);
      switch (mode.get()) {
        case NO_RELIST -> context.resourceOperations().serverSideApply(cm, configMapEventSource);
        case RELIST_AROUND_UPDATE -> {
          configMapEventSource.simulateOnBeforeList();
          context.resourceOperations().serverSideApply(cm, configMapEventSource);
          configMapEventSource.simulateOnList();
        }
        case RELIST_COMPLETES_BEFORE_UPDATE -> {
          configMapEventSource.simulateOnBeforeList();
          configMapEventSource.simulateOnList();
          context.resourceOperations().serverSideApply(cm, configMapEventSource);
        }
        case RELIST_STARTS_DURING_UPDATE -> {
          // Drive the event-filtering update path manually so we can fire onBeforeList AFTER the
          // window has been opened by startEventFilteringModify but BEFORE the SSA hits the API.
          var fieldManager = context.getControllerConfiguration().fieldManager();
          configMapEventSource.eventFilteringUpdateAndCacheResource(
              cm,
              r -> {
                configMapEventSource.simulateOnBeforeList();
                return context
                    .getClient()
                    .resource(r)
                    .patch(
                        new PatchContext.Builder()
                            .withForce(true)
                            .withFieldManager(fieldManager)
                            .withPatchType(PatchType.SERVER_SIDE_APPLY)
                            .build());
              });
          configMapEventSource.simulateOnList();
        }
      }
    }

    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, OnRelistFilterCustomResource>> prepareEventSources(
      EventSourceContext<OnRelistFilterCustomResource> context) {
    configMapEventSource =
        new RelistAwareInformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, OnRelistFilterCustomResource.class)
                .build(),
            context);
    return List.of(configMapEventSource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public void setMode(Mode value) {
    mode.set(value);
  }

  private static ConfigMap prepareConfigMap(OnRelistFilterCustomResource p) {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(p.getMetadata().getName())
                    .withNamespace(p.getMetadata().getNamespace())
                    .build())
            .withData(Map.of(CM_DATA_KEY, "1"))
            .build();
    cm.addOwnerReference(p);
    return cm;
  }

  /**
   * Subclass exposing the {@code onBeforeList}/{@code onList} callbacks so a test can simulate the
   * informer's re-list lifecycle around an own write, without relying on actual watch
   * disconnections.
   */
  static class RelistAwareInformerEventSource<R extends HasMetadata, P extends HasMetadata>
      extends InformerEventSource<R, P> {

    RelistAwareInformerEventSource(
        InformerEventSourceConfiguration<R> configuration, EventSourceContext<P> context) {
      super(configuration, context);
    }

    void simulateOnBeforeList() {
      // uncomment when fabric8 supports re-list
      //      onBeforeList(null);
    }

    void simulateOnList() {
      onList(null, false);
    }
  }
}
