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
package io.javaoperatorsdk.operator.baseapi.triggerallevent.finalizerhandling;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The test showcases how manage finalizers only on some of the custom resources using
 * `triggerReconcilerOnAllEvent` mode.
 */
public class SelectiveFinalizerHandlingIT {

  public static final String TEST_RESOURCE2 = "test2";
  public static final String TEST_RESOURCE1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(SelectiveFinalizerHandlingReconciler.class)
          .build();

  @Test
  void addFinalizerOnlyOnSomeOfTheResources() {
    var resource1 = extension.create(testResource(TEST_RESOURCE1, true));
    var resource2 = extension.create(testResource(TEST_RESOURCE2, false));

    await()
        .pollDelay(Duration.ofMillis(100))
        .untilAsserted(
            () -> {
              var res1 =
                  extension.get(
                      SelectiveFinalizerHandlingReconcilerCustomResource.class, TEST_RESOURCE1);
              var res2 =
                  extension.get(
                      SelectiveFinalizerHandlingReconcilerCustomResource.class, TEST_RESOURCE2);

              assertThat(res1.getFinalizers()).isNotEmpty();
              assertThat(res2.getFinalizers()).isEmpty();
            });

    extension.delete(resource1);
    extension.delete(resource2);

    await()
        .untilAsserted(
            () -> {
              var res1 =
                  extension.get(
                      SelectiveFinalizerHandlingReconcilerCustomResource.class, TEST_RESOURCE1);
              var res2 =
                  extension.get(
                      SelectiveFinalizerHandlingReconcilerCustomResource.class, TEST_RESOURCE2);

              assertThat(res1).isNull();
              assertThat(res2).isNull();
            });
  }

  SelectiveFinalizerHandlingReconcilerCustomResource testResource(
      String name, boolean addFinalizer) {
    var resource = new SelectiveFinalizerHandlingReconcilerCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(name).build());
    resource.setSpec(new SelectiveFinalizerHandlingReconcilerSpec());
    resource.getSpec().setUseFinalizer(addFinalizer);

    return resource;
  }
}
