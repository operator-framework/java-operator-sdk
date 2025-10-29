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
package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;

public class GenericKubernetesDependentResource<P extends HasMetadata>
    extends KubernetesDependentResource<GenericKubernetesResource, P> {

  private final GroupVersionKindPlural groupVersionKind;

  public GenericKubernetesDependentResource(GroupVersionKind groupVersionKind) {
    this(GroupVersionKindPlural.from(groupVersionKind));
  }

  public GenericKubernetesDependentResource(GroupVersionKind groupVersionKind, String name) {
    this(GroupVersionKindPlural.from(groupVersionKind), name);
  }

  public GenericKubernetesDependentResource(GroupVersionKindPlural groupVersionKind) {
    super(GenericKubernetesResource.class, null);
    this.groupVersionKind = groupVersionKind;
  }

  public GenericKubernetesDependentResource(GroupVersionKindPlural groupVersionKind, String name) {
    super(GenericKubernetesResource.class, name);
    this.groupVersionKind = groupVersionKind;
  }

  protected InformerEventSourceConfiguration.Builder<GenericKubernetesResource>
      informerConfigurationBuilder(EventSourceContext<P> context) {
    return InformerEventSourceConfiguration.from(
        groupVersionKind, context.getPrimaryResourceClass());
  }

  @SuppressWarnings("unused")
  public GroupVersionKindPlural getGroupVersionKind() {
    return groupVersionKind;
  }
}
