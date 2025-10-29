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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.ItemStore;

public class TransformingItemStore<R extends HasMetadata> implements ItemStore<R> {

  private final Function<R, String> keyFunction;
  private final UnaryOperator<R> transformationFunction;
  private final ConcurrentHashMap<String, R> store = new ConcurrentHashMap<>();

  public TransformingItemStore(UnaryOperator<R> transformationFunction) {
    this(Cache::metaNamespaceKeyFunc, transformationFunction);
  }

  public TransformingItemStore(
      Function<R, String> keyFunction, UnaryOperator<R> transformationFunction) {
    this.keyFunction = keyFunction;
    this.transformationFunction = transformationFunction;
  }

  @Override
  public String getKey(R obj) {
    return keyFunction.apply(obj);
  }

  @Override
  public R put(String key, R obj) {
    var originalName = obj.getMetadata().getName();
    var originalNamespace = obj.getMetadata().getNamespace();
    var originalResourceVersion = obj.getMetadata().getResourceVersion();

    var transformed = transformationFunction.apply(obj);

    transformed.getMetadata().setName(originalName);
    transformed.getMetadata().setNamespace(originalNamespace);
    transformed.getMetadata().setResourceVersion(originalResourceVersion);
    return store.put(key, transformed);
  }

  @Override
  public R remove(String key) {
    return store.remove(key);
  }

  @Override
  public Stream<String> keySet() {
    return store.keySet().stream();
  }

  @Override
  public Stream<R> values() {
    return store.values().stream();
  }

  @Override
  public R get(String key) {
    return store.get(key);
  }

  @Override
  public int size() {
    return store.size();
  }

  @Override
  public boolean isFullState() {
    return false;
  }
}
