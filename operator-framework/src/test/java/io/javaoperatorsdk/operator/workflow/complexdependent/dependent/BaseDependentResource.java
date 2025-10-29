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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.workflow.complexdependent.ComplexWorkflowCustomResource;

public abstract class BaseDependentResource<R extends HasMetadata>
    extends CRUDKubernetesDependentResource<R, ComplexWorkflowCustomResource> {

  public static final String K8S_NAME = "app.kubernetes.io/name";
  protected final String component;

  public BaseDependentResource(Class<R> resourceType, String component) {
    super(resourceType);
    this.component = component;
  }

  protected String name(ComplexWorkflowCustomResource primary) {
    return String.format("%s-%s", component, primary.getSpec().getProjectId());
  }

  protected ObjectMetaBuilder createMeta(ComplexWorkflowCustomResource primary) {
    String name = name(primary);
    return new ObjectMetaBuilder()
        .withName(name)
        .withNamespace(primary.getMetadata().getNamespace())
        .addToLabels(K8S_NAME, name);
  }
}
