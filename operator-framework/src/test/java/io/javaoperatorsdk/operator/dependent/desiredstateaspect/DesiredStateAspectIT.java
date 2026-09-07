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
package io.javaoperatorsdk.operator.dependent.desiredstateaspect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Common metadata on all managed resources using desired state aspects",
    description =
        """
        Demonstrates how to register a global DesiredStateAspect on the ConfigurationService in \
        order to add common metadata, here labels identifying the operator and the dependent \
        resource the secondary resource originates from, to every Kubernetes resource managed by \
        the operator. Aspects are applied to the desired state right after it is computed, so the \
        added metadata is also taken into account when matching the actual resource against its \
        desired state.
        """)
class DesiredStateAspectIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String MANAGED_BY_LABEL_KEY = "app.kubernetes.io/managed-by";
  public static final String MANAGED_BY_LABEL_VALUE = "desired-state-aspect-operator";
  public static final String DEPENDENT_LABEL_KEY = "javaoperatorsdk.io/dependent";
  public static final String DEPENDENT_LABEL_VALUE =
      DesiredStateAspectReconciler.ConfigMapDependentResource.class.getSimpleName().toLowerCase();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(DesiredStateAspectReconciler.class)
          .withConfigurationService(
              o ->
                  o.addDesiredStateAspects(
                      (desired, dependentResource, context) ->
                          desired
                              .getMetadata()
                              .getLabels()
                              .put(MANAGED_BY_LABEL_KEY, MANAGED_BY_LABEL_VALUE),
                      (desired, dependentResource, context) ->
                          desired
                              .getMetadata()
                              .getLabels()
                              .put(
                                  DEPENDENT_LABEL_KEY,
                                  dependentResource.getClass().getSimpleName().toLowerCase())))
          .build();

  @Test
  void aspectsAreAppliedToAllManagedResources() {
    operator.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(configMap).isNotNull();
              assertThat(configMap.getMetadata().getLabels())
                  .containsEntry(MANAGED_BY_LABEL_KEY, MANAGED_BY_LABEL_VALUE)
                  .containsEntry(DEPENDENT_LABEL_KEY, DEPENDENT_LABEL_VALUE);
            });
  }

  @Test
  void metadataAddedByAspectsIsRestoredIfRemoved() {
    operator.create(testResource());

    await()
        .untilAsserted(
            () ->
                assertThat(operator.get(ConfigMap.class, TEST_RESOURCE_NAME))
                    .isNotNull()
                    .extracting(cm -> cm.getMetadata().getLabels())
                    .satisfies(
                        labels ->
                            assertThat(labels)
                                .containsEntry(MANAGED_BY_LABEL_KEY, MANAGED_BY_LABEL_VALUE)));

    var configMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME);
    configMap.getMetadata().getLabels().remove(MANAGED_BY_LABEL_KEY);
    operator.replace(configMap);

    await()
        .untilAsserted(
            () ->
                assertThat(
                        operator.get(ConfigMap.class, TEST_RESOURCE_NAME).getMetadata().getLabels())
                    .containsEntry(MANAGED_BY_LABEL_KEY, MANAGED_BY_LABEL_VALUE));
  }

  DesiredStateAspectCustomResource testResource() {
    var res = new DesiredStateAspectCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}
