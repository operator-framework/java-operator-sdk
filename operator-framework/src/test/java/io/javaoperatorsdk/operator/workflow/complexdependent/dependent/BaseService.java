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

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.workflow.complexdependent.ComplexWorkflowCustomResource;

public abstract class BaseService extends BaseDependentResource<Service> {

  public BaseService(String component) {
    super(Service.class, component);
  }

  @Override
  protected Service desired(
      ComplexWorkflowCustomResource primary, Context<ComplexWorkflowCustomResource> context) {
    var template =
        ReconcilerUtils.loadYaml(
            Service.class,
            getClass(),
            "/io/javaoperatorsdk/operator/workflow/complexdependent/service.yaml");

    return new ServiceBuilder(template)
        .withMetadata(createMeta(primary).build())
        .editOrNewSpec()
        .withSelector(Map.of(K8S_NAME, name(primary)))
        .endSpec()
        .build();
  }
}
