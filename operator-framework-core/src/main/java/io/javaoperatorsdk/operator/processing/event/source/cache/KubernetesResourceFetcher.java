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
package io.javaoperatorsdk.operator.processing.event.source.cache;

import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class KubernetesResourceFetcher<R extends HasMetadata>
    implements ResourceFetcher<String, R> {

  private final Class<R> rClass;
  private final KubernetesClient client;
  private final Function<String, ResourceID> resourceIDFunction;

  public KubernetesResourceFetcher(Class<R> rClass, KubernetesClient client) {
    this(rClass, client, inverseNamespaceKeyFunction());
  }

  public KubernetesResourceFetcher(
      Class<R> rClass, KubernetesClient client, Function<String, ResourceID> resourceIDFunction) {
    this.rClass = rClass;
    this.client = client;
    this.resourceIDFunction = resourceIDFunction;
  }

  @Override
  public R fetchResource(String key) {
    var resourceId = resourceIDFunction.apply(key);
    return resourceId
        .getNamespace()
        .map(ns -> client.resources(rClass).inNamespace(ns).withName(resourceId.getName()).get())
        .orElse(client.resources(rClass).withName(resourceId.getName()).get());
  }

  public static Function<String, ResourceID> inverseNamespaceKeyFunction() {
    return s -> {
      int delimiterIndex = s.indexOf("/");
      if (delimiterIndex == -1) {
        return new ResourceID(s);
      } else {
        return new ResourceID(s.substring(delimiterIndex + 1), s.substring(0, delimiterIndex));
      }
    };
  }
}
