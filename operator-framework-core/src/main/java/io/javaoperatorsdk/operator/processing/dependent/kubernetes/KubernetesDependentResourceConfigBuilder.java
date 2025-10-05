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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;

public final class KubernetesDependentResourceConfigBuilder<R extends HasMetadata> {

  private boolean createResourceOnlyIfNotExistingWithSSA;
  private Boolean useSSA = null;
  private InformerConfiguration<R> informerConfiguration;
  private SSABasedGenericKubernetesResourceMatcher<R> matcher;

  public KubernetesDependentResourceConfigBuilder() {}

  @SuppressWarnings("unused")
  public KubernetesDependentResourceConfigBuilder<R> withCreateResourceOnlyIfNotExistingWithSSA(
      boolean createResourceOnlyIfNotExistingWithSSA) {
    this.createResourceOnlyIfNotExistingWithSSA = createResourceOnlyIfNotExistingWithSSA;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withUseSSA(boolean useSSA) {
    this.useSSA = useSSA;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withKubernetesDependentInformerConfig(
      InformerConfiguration<R> informerConfiguration) {
    this.informerConfiguration = informerConfiguration;
    return this;
  }

  public KubernetesDependentResourceConfigBuilder<R> withSSAMatcher(
      SSABasedGenericKubernetesResourceMatcher<R> matcher) {
    this.matcher = matcher;
    return this;
  }

  public KubernetesDependentResourceConfig<R> build() {
    return new KubernetesDependentResourceConfig<>(
        useSSA, createResourceOnlyIfNotExistingWithSSA, informerConfiguration, matcher);
  }
}
