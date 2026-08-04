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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface KubernetesDependent {

  /** Creates the resource only if did not exist before, this applies only if SSA is used. */
  boolean createResourceOnlyIfNotExistingWithSSA() default
      KubernetesDependentResourceConfig.DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA;

  /**
   * Determines whether to use SSA (Server-Side Apply) for this dependent. If SSA is used, the
   * dependent resource will only be created if it did not exist before. Default value is {@link
   * BooleanWithUndefined#UNDEFINED}, which specifies that the behavior with respect to SSA is
   * inherited from the global configuration.
   *
   * @return {@code true} if SSA is enabled, {@code false} if SSA is disabled, {@link
   *     BooleanWithUndefined#UNDEFINED} if the SSA behavior should be inherited from the global
   *     configuration
   */
  BooleanWithUndefined useSSA() default BooleanWithUndefined.UNDEFINED;

  /**
   * The underlying Informer event source configuration
   *
   * @return the {@link Informer} configuration
   */
  Informer informer() default @Informer;

  /**
   * The specific matcher implementation to use when Server-Side Apply (SSA) is used, when case the
   * default one isn't working appropriately. Typically, this could be needed to cover border cases
   * with some Kubernetes resources that are modified by their controllers to normalize or add
   * default values, which could result in infinite loops with the default matcher. Using a specific
   * matcher could also be an optimization decision if determination of whether two resources match
   * can be done faster than what can be done with the default exhaustive algorithm.
   *
   * @return the class of the specific matcher to use for the associated dependent resources
   * @since 5.1
   */
  Class<? extends SSABasedGenericKubernetesResourceMatcher> matcher() default
      SSABasedGenericKubernetesResourceMatcher.class;

  /**
   * Whether JOSDK should detect that the API version of this dependent resource's desired state has
   * changed since it was last applied by the operator (for example after the operator was upgraded
   * to target a new CRD version) and, in that case, request a one-time update of the actual
   * resource.
   *
   * <p>When enabled, JOSDK records the API version it applies in the {@value
   * KubernetesDependentResource#LAST_APPLIED_API_VERSION_ANNOTATION_KEY} annotation. On subsequent
   * reconciliations, the resource is considered mismatched (and thus updated) if that recorded
   * marker differs from the API version the operator currently uses, including when the marker is
   * missing entirely (for example on resources created before this feature was enabled). Once the
   * resource has been updated, the marker matches the current API version again, so no further
   * update is requested until the API version changes again.
   *
   * <p>This is opt-in and disabled by default: when disabled, no marker annotation is ever added or
   * read, and matching behavior is unchanged. It does not read or infer the actual storage version
   * of the resource in Kubernetes, since that information is not reliably exposed by the API
   * server; it only tracks what the operator itself last applied. It is not a replacement for
   * Kubernetes' <a
   * href="https://kubernetes.io/docs/tasks/manage-kubernetes-objects/storage-version-migration/">StorageVersionMigration</a>.
   *
   * @return {@code true} if API version change detection is enabled, {@code false} otherwise
   * @since 5.6
   */
  @Experimental(API_MIGHT_CHANGE)
  boolean detectApiVersionChange() default
      KubernetesDependentResourceConfig.DEFAULT_DETECT_API_VERSION_CHANGE;
}
