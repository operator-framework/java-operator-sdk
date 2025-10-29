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
package io.javaoperatorsdk.operator.baseapi.primaryindexer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class AbstractPrimaryIndexerTestReconciler
    implements Reconciler<PrimaryIndexerTestCustomResource> {

  public static final String CONFIG_MAP_NAME = "common-config-map";

  private final Map<String, AtomicInteger> numberOfExecutions = new ConcurrentHashMap<>();

  protected static final String CONFIG_MAP_RELATION_INDEXER = "cm-indexer";

  protected static final Function<PrimaryIndexerTestCustomResource, List<String>> indexer =
      resource -> List.of(resource.getSpec().getConfigMapName());

  @Override
  public UpdateControl<PrimaryIndexerTestCustomResource> reconcile(
      PrimaryIndexerTestCustomResource resource,
      Context<PrimaryIndexerTestCustomResource> context) {
    numberOfExecutions.computeIfAbsent(resource.getMetadata().getName(), r -> new AtomicInteger(0));
    numberOfExecutions.get(resource.getMetadata().getName()).incrementAndGet();
    return UpdateControl.noUpdate();
  }

  public Map<String, AtomicInteger> getNumberOfExecutions() {
    return numberOfExecutions;
  }
}
