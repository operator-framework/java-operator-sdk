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
package io.javaoperatorsdk.operator.processing.event.source;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.UnaryOperator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;

public class EventFilterTestUtils {

  static ExecutorService executorService = Executors.newCachedThreadPool();

  public static <R extends HasMetadata> CountDownLatch sendForEventFilteringUpdate(
      ManagedInformerEventSource<R, ?, ?> eventSource, R resource, UnaryOperator<R> updateMethod) {
    try {
      CountDownLatch latch = new CountDownLatch(1);
      CountDownLatch sendOnGoingLatch = new CountDownLatch(1);
      executorService.submit(
          () ->
              eventSource.eventFilteringUpdateAndCacheResource(
                  resource,
                  r -> {
                    try {
                      sendOnGoingLatch.countDown();
                      latch.await();
                      var resp = updateMethod.apply(r);
                      return resp;
                    } catch (InterruptedException e) {
                      throw new RuntimeException(e);
                    }
                  }));
      sendOnGoingLatch.await();
      return latch;
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static <R extends HasMetadata> R withResourceVersion(R resource, int resourceVersion) {
    var v = resource.getMetadata().getResourceVersion();
    if (v == null) {
      throw new IllegalArgumentException("Resource version is null");
    }
    resource.getMetadata().setResourceVersion("" + resourceVersion);
    return resource;
  }
}
