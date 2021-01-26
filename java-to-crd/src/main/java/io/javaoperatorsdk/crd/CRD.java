package io.javaoperatorsdk.crd;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface CRD {

  /**
   * The custom resource short name.
   *
   * @return The short name, or a calculated short name (based on camelcase) if shortname is empty.
   */
  String[] shortNames() default {};

  /**
   * Flag that indicates that version is enabled.
   *
   * @return true if version is enabled.
   */
  boolean served() default true;

  /**
   * Flag that indicates that the version is the storage version. Only one version should be set as
   * 'storage' at a time.
   *
   * @return true if version is the storage version.
   */
  boolean storage() default false;

  /**
   * The resource scope.
   *
   * @return The scope, defaults to Namespaced.
   */
  Scope scope() default Scope.Namespaced;

  enum Scope {
    Namespaced,
    Cluster;
  }
}
