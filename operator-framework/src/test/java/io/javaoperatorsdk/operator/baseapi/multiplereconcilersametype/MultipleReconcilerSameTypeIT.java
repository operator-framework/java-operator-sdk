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
package io.javaoperatorsdk.operator.baseapi.multiplereconcilersametype;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MultipleReconcilerSameTypeIT {

  public static final String TEST_RESOURCE_1 = "test1";
  public static final String TEST_RESOURCE_2 = "test2";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(MultipleReconcilerSameTypeReconciler1.class)
          .withReconciler(MultipleReconcilerSameTypeReconciler2.class)
          .build();

  @Test
  void multipleReconcilersBasedOnLeaderElection() {
    extension.create(testResource(TEST_RESOURCE_1, true));
    extension.create(testResource(TEST_RESOURCE_2, false));

    await()
        .untilAsserted(
            () -> {
              assertThat(
                      extension
                          .getReconcilerOfType(MultipleReconcilerSameTypeReconciler1.class)
                          .getNumberOfExecutions())
                  .isEqualTo(1);
              assertThat(
                      extension
                          .getReconcilerOfType(MultipleReconcilerSameTypeReconciler2.class)
                          .getNumberOfExecutions())
                  .isEqualTo(1);

              var res1 =
                  extension.get(MultipleReconcilerSameTypeCustomResource.class, TEST_RESOURCE_1);
              var res2 =
                  extension.get(MultipleReconcilerSameTypeCustomResource.class, TEST_RESOURCE_2);
              assertThat(res1).isNotNull();
              assertThat(res2).isNotNull();
              assertThat(res1.getStatus().getReconciledBy())
                  .isEqualTo(MultipleReconcilerSameTypeReconciler1.class.getSimpleName());
              assertThat(res2.getStatus().getReconciledBy())
                  .isEqualTo(MultipleReconcilerSameTypeReconciler2.class.getSimpleName());
            });
  }

  MultipleReconcilerSameTypeCustomResource testResource(String name, boolean type1) {
    var res = new MultipleReconcilerSameTypeCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(name).build());
    if (type1) {
      res.getMetadata().getLabels().put("reconciler", "1");
    }
    return res;
  }
}
