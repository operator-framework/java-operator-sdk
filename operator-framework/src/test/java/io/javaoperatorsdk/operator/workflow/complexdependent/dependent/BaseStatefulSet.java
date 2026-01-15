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
package io.javaoperatorsdk.operator.workflow.complexdependent.dependent;

import java.util.Map;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.workflow.complexdependent.ComplexWorkflowCustomResource;

public abstract class BaseStatefulSet extends BaseDependentResource<StatefulSet> {
  public BaseStatefulSet(String component) {
    super(StatefulSet.class, component);
  }

  @Override
  protected StatefulSet desired(
      ComplexWorkflowCustomResource primary, Context<ComplexWorkflowCustomResource> context) {
    var template =
        ReconcilerUtilsInternal.loadYaml(
            StatefulSet.class,
            getClass(),
            "/io/javaoperatorsdk/operator/workflow/complexdependent/statefulset.yaml");
    var name = name(primary);
    var metadata = createMeta(primary).build();

    return new StatefulSetBuilder(template)
        .withMetadata(metadata)
        .editSpec()
        .withServiceName(name)
        .editOrNewSelector()
        .withMatchLabels(Map.of(K8S_NAME, name))
        .endSelector()
        .editTemplate()
        .withMetadata(metadata)
        .endTemplate()
        .editFirstVolumeClaimTemplate()
        .editMetadata()
        .withLabels(Map.of(K8S_NAME, name))
        .endMetadata()
        .endVolumeClaimTemplate()
        .endSpec()
        .build();
  }
}
