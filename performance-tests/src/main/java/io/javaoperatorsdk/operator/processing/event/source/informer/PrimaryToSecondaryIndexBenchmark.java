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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Benchmark)
public class PrimaryToSecondaryIndexBenchmark {

  @Param({"10", "100", "1000"})
  private int indexSize;

  private DefaultPrimaryToSecondaryIndex<ConfigMap> index;
  private ConfigMap sampleResource;
  private ResourceID samplePrimaryId;

  @Setup(Level.Iteration)
  public void setup() {
    ResourceID primaryId = new ResourceID("primary-0", "default");
    index = new DefaultPrimaryToSecondaryIndex<>(resource -> Set.of(primaryId));

    for (int i = 0; i < indexSize; i++) {
      ConfigMap cm = createConfigMap("secondary-" + i);
      index.onAddOrUpdate(cm);
    }

    sampleResource = createConfigMap("new-secondary");
    samplePrimaryId = primaryId;
  }

  @Benchmark
  public void onAddOrUpdate() {
    index.onAddOrUpdate(sampleResource);
  }

  @Benchmark
  public void getSecondaryResources(Blackhole bh) {
    bh.consume(index.getSecondaryResources(samplePrimaryId));
  }

  @Benchmark
  public void onDelete() {
    index.onDelete(sampleResource);
  }

  @Benchmark
  public void addThenDelete() {
    index.onAddOrUpdate(sampleResource);
    index.onDelete(sampleResource);
  }

  private static ConfigMap createConfigMap(String name) {
    ConfigMap cm = new ConfigMap();
    cm.setMetadata(
        new ObjectMetaBuilder()
            .withName(name)
            .withNamespace("default")
            .withResourceVersion("1")
            .build());
    return cm;
  }
}
