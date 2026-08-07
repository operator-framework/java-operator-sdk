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

public class KubernetesDependentResourceConfig<R extends HasMetadata> {

  public static final boolean DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA = true;
  public static final boolean DEFAULT_DETECT_API_VERSION_CHANGE = false;

  private final Boolean useSSA;
  private final boolean createResourceOnlyIfNotExistingWithSSA;
  private final InformerConfiguration<R> informerConfig;
  private final SSABasedGenericKubernetesResourceMatcher<R> matcher;
  private final boolean detectApiVersionChange;

  public KubernetesDependentResourceConfig(
      Boolean useSSA,
      boolean createResourceOnlyIfNotExistingWithSSA,
      InformerConfiguration<R> informerConfig) {
    this(useSSA, createResourceOnlyIfNotExistingWithSSA, informerConfig, null);
  }

  public KubernetesDependentResourceConfig(
      Boolean useSSA,
      boolean createResourceOnlyIfNotExistingWithSSA,
      InformerConfiguration<R> informerConfig,
      SSABasedGenericKubernetesResourceMatcher<R> matcher) {
    this(
        useSSA,
        createResourceOnlyIfNotExistingWithSSA,
        informerConfig,
        matcher,
        DEFAULT_DETECT_API_VERSION_CHANGE);
  }

  public KubernetesDependentResourceConfig(
      Boolean useSSA,
      boolean createResourceOnlyIfNotExistingWithSSA,
      InformerConfiguration<R> informerConfig,
      SSABasedGenericKubernetesResourceMatcher<R> matcher,
      boolean detectApiVersionChange) {
    this.useSSA = useSSA;
    this.createResourceOnlyIfNotExistingWithSSA = createResourceOnlyIfNotExistingWithSSA;
    this.informerConfig = informerConfig;
    this.matcher =
        matcher != null ? matcher : SSABasedGenericKubernetesResourceMatcher.getInstance();
    this.detectApiVersionChange = detectApiVersionChange;
  }

  public boolean createResourceOnlyIfNotExistingWithSSA() {
    return createResourceOnlyIfNotExistingWithSSA;
  }

  public Boolean useSSA() {
    return useSSA;
  }

  public InformerConfiguration<R> informerConfig() {
    return informerConfig;
  }

  public SSABasedGenericKubernetesResourceMatcher<R> matcher() {
    return matcher;
  }

  /**
   * Whether JOSDK should detect when the API version of this dependent resource's desired state has
   * changed since it was last applied by the operator and, in that case, request a one-time update
   * of the actual resource.
   *
   * @return {@code true} if API version change detection is enabled, {@code false} otherwise
   * @since 5.6
   */
  public boolean detectApiVersionChange() {
    return detectApiVersionChange;
  }
}
