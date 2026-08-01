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
package io.javaoperatorsdk.operator.processing.event.source.informer.pool;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.OperatorException;

@SuppressWarnings({"unchecked", "rawtypes"})
public class NonSharingInformerPool extends AbstractInformerPool {

  private static final Logger log = LoggerFactory.getLogger(NonSharingInformerPool.class);

  private final Map<ClassifierWithName, SharedIndexInformer> informers = new ConcurrentHashMap();

  @Override
  public synchronized <R extends HasMetadata> SharedIndexInformer<R> getInformer(
      String controllerName, String name, InformerClassifier<R> classifier) {
    var key = new ClassifierWithName(controllerName, name, classifier);
    if (informers.containsKey(key)) {
      throw new OperatorException(
          "Informer already registered for controller: "
              + controllerName
              + ", event source: "
              + name
              + ", classifier: "
              + classifier
              + ". This pool creates a dedicated informer per controller/event source and never"
              + " shares them, so requesting one twice for the same combination without releasing"
              + " the previous one first would leak the earlier informer.");
    }
    var informer = createInformer(classifier);
    informers.put(key, informer);
    return informer;
  }

  @Override
  public <R extends HasMetadata> Optional<SharedIndexInformer<R>> releaseInformer(
      String controllerName, String name, InformerClassifier<R> classifier) {
    var informer = informers.remove(new ClassifierWithName(controllerName, name, classifier));
    if (informer != null) {
      informer.stop();
    } else {
      log.warn("Informer was not found for classifier: {}", classifier);
    }
    return Optional.ofNullable(informer);
  }

  /** Number of informers currently tracked (i.e. created but not yet released). */
  int size() {
    return informers.size();
  }

  /**
   * Number of distinct informers currently held for the given resource type. Since this pool never
   * shares informers, this equals the number of registered users (controller + event source name)
   * watching that resource type.
   */
  @Override
  public long numberOfInformersForResource(Class<? extends HasMetadata> resourceClass) {
    return informers.keySet().stream()
        .filter(key -> resourceClass.equals(key.classifier().resourceClass()))
        .count();
  }

  public record ClassifierWithName<R extends HasMetadata>(
      String controllerName, String name, InformerClassifier<R> classifier) {}
}
