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

import java.time.Duration;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestCustomResource;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestReconciler;
import io.javaoperatorsdk.operator.processing.event.source.cache.sample.namespacescope.BoundedCacheTestSpec;

import static io.javaoperatorsdk.operator.processing.event.source.cache.sample.AbstractTestReconciler.boundedItemStore;

class CaffeineBoundedCacheNamespacedIT
    extends BoundedCacheTestBase<BoundedCacheTestCustomResource> {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              new BoundedCacheTestReconciler(),
              o -> {
                o.withItemStore(
                    boundedItemStore(
                        new KubernetesClientBuilder().build(),
                        BoundedCacheTestCustomResource.class,
                        Duration.ofMinutes(1),
                        1));
              })
          .build();

  BoundedCacheTestCustomResource createTestResource(int index) {
    var res = new BoundedCacheTestCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME_PREFIX + index).build());
    res.setSpec(new BoundedCacheTestSpec());
    res.getSpec().setData(INITIAL_DATA_PREFIX + index);
    res.getSpec().setTargetNamespace(extension.getNamespace());
    return res;
  }

  @Override
  Class<BoundedCacheTestCustomResource> customResourceClass() {
    return BoundedCacheTestCustomResource.class;
  }

  @Override
  LocallyRunOperatorExtension extension() {
    return extension;
  }
}
