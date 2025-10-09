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
package io.javaoperatorsdk.operator.baseapi.updatestatusincleanupandreschedule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Update Status in Cleanup and Reschedule",
    description =
        """
        Tests the ability to update resource status during cleanup and reschedule the cleanup \
        operation. This demonstrates that cleanup methods can perform status updates and request \
        to be called again after a delay, enabling multi-step cleanup processes with status \
        tracking.
        """)
public class UpdateStatusInCleanupAndRescheduleIT {

  public static final String TEST_RESOURCE = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(UpdateStatusInCleanupAndRescheduleReconciler.class)
          .build();

  @Test
  void testRescheduleAfterPatch() {
    var res = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var resource =
                  extension.get(
                      UpdateStatusInCleanupAndRescheduleCustomResource.class, TEST_RESOURCE);
              assertThat(resource.getMetadata().getFinalizers()).isNotEmpty();
            });

    extension.delete(res);

    await()
        .untilAsserted(
            () -> {
              var resource =
                  extension.get(
                      UpdateStatusInCleanupAndRescheduleCustomResource.class, TEST_RESOURCE);
              assertThat(resource).isNull();
            });

    assertThat(
            extension
                .getReconcilerOfType(UpdateStatusInCleanupAndRescheduleReconciler.class)
                .getRescheduleDelayWorked())
        .isTrue();
  }

  UpdateStatusInCleanupAndRescheduleCustomResource testResource() {
    var resource = new UpdateStatusInCleanupAndRescheduleCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE).build());
    return resource;
  }
}
