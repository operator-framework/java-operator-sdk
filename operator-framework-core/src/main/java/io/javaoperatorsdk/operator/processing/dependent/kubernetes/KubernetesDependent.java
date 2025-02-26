package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.javaoperatorsdk.operator.api.config.informer.Informer;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface KubernetesDependent {

  /**
   * Creates the resource only if did not exist before, this applies only if SSA is used.
   */
  boolean createResourceOnlyIfNotExistingWithSSA() default
      KubernetesDependentResourceConfig.DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA;

  /**
   * Determines whether to use SSA (Server-Side Apply) for this dependent. If SSA is used, the
   * dependent resource will only be created if it did not exist before. Default value is
   * {@link BooleanWithUndefined#UNDEFINED}, which specifies that the behavior with respect to SSA
   * is inherited from the global configuration.
   *
   * @return {@code true} if SSA is enabled, {@code false} if SSA is disabled,
   *         {@link BooleanWithUndefined#UNDEFINED} if the SSA behavior should be inherited from the
   *         global configuration
   */
  BooleanWithUndefined useSSA() default BooleanWithUndefined.UNDEFINED;

  Informer informer() default @Informer;
}
