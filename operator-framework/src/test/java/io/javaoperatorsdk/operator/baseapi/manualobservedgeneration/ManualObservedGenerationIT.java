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
package io.javaoperatorsdk.operator.baseapi.manualobservedgeneration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ManualObservedGenerationIT {

  public static final String RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ManualObservedGenerationReconciler())
          .build();

  @Test
  void observedGenerationUpdated() {
    extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var r = extension.get(ManualObservedGenerationCustomResource.class, RESOURCE_NAME);
              assertThat(r).isNotNull();
              assertThat(r.getStatus().getObservedGeneration()).isEqualTo(1);
              assertThat(r.getStatus().getObservedGeneration())
                  .isEqualTo(r.getMetadata().getGeneration());
            });

    var changed = testResource();
    changed.getSpec().setValue("changed value");
    extension.replace(changed);

    await()
        .untilAsserted(
            () -> {
              var r = extension.get(ManualObservedGenerationCustomResource.class, RESOURCE_NAME);
              assertThat(r.getStatus().getObservedGeneration()).isEqualTo(2);
              assertThat(r.getStatus().getObservedGeneration())
                  .isEqualTo(r.getMetadata().getGeneration());
            });
  }

  ManualObservedGenerationCustomResource testResource() {
    var res = new ManualObservedGenerationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    res.setSpec(new ManualObservedGenerationSpec());
    res.getSpec().setValue("Initial Value");
    return res;
  }
}
