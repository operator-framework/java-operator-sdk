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
package io.javaoperatorsdk.operator.api.config;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.EmptyTestDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilsTest {

  @Test
  void shouldNotCheckCRDAndValidateLocalModelByDefault() {
    assertFalse(Utils.shouldCheckCRDAndValidateLocalModel());
  }

  @Test
  void shouldNotDebugThreadPoolByDefault() {
    assertFalse(Utils.debugThreadPool());
  }

  @Test
  void askingForNonexistentPropertyShouldReturnDefault() {
    final var key = "foo";
    assertNull(System.getProperty(key));
    assertFalse(Utils.getBooleanFromSystemPropsOrDefault(key, false));
    assertTrue(Utils.getBooleanFromSystemPropsOrDefault(key, true));
  }

  @Test
  void askingForExistingPropertyShouldReturnItIfBoolean() {
    final var key = "foo";
    try {
      System.setProperty(key, "true");
      assertNotNull(System.getProperty(key));
      assertTrue(Utils.getBooleanFromSystemPropsOrDefault(key, false));
      assertTrue(Utils.getBooleanFromSystemPropsOrDefault(key, true));

      System.setProperty(key, "TruE");
      assertTrue(Utils.getBooleanFromSystemPropsOrDefault(key, false));
      assertTrue(Utils.getBooleanFromSystemPropsOrDefault(key, true));

      System.setProperty(key, " \tTRUE  ");
      assertTrue(Utils.getBooleanFromSystemPropsOrDefault(key, false));
      assertTrue(Utils.getBooleanFromSystemPropsOrDefault(key, true));

      System.setProperty(key, " \nFalSe \t ");
      assertFalse(Utils.getBooleanFromSystemPropsOrDefault(key, false));
      assertFalse(Utils.getBooleanFromSystemPropsOrDefault(key, true));
    } finally {
      System.clearProperty(key);
    }
  }

  @Test
  void askingForExistingNonBooleanPropertyShouldReturnDefaultValue() {
    final var key = "foo";
    try {
      System.setProperty(key, "bar");
      assertNotNull(System.getProperty(key));
      assertFalse(Utils.getBooleanFromSystemPropsOrDefault(key, false));
      assertTrue(Utils.getBooleanFromSystemPropsOrDefault(key, true));
    } finally {
      System.clearProperty(key);
    }
  }

  @Test
  void getsFirstTypeArgumentFromExtendedClass() {
    Class<?> res =
        Utils.getFirstTypeArgumentFromExtendedClass(TestKubernetesDependentResource.class);
    assertThat(res).isEqualTo(Deployment.class);
  }

  @Test
  void getsFirstTypeArgumentFromInterface() {
    assertThat(
            Utils.getFirstTypeArgumentFromInterface(
                EmptyTestDependentResource.class, DependentResource.class))
        .isEqualTo(Deployment.class);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                Utils.getFirstTypeArgumentFromInterface(
                    TestKubernetesDependentResource.class, DependentResource.class));
  }

  @Test
  void getsFirstTypeArgumentFromInterfaceFromParent() {
    assertThat(
            Utils.getFirstTypeArgumentFromSuperClassOrInterface(
                ConcreteReconciler.class, Reconciler.class))
        .isEqualTo(ConfigMap.class);
  }

  public abstract static class AbstractReconciler<P extends HasMetadata> implements Reconciler<P> {}

  public static class ConcreteReconciler extends AbstractReconciler<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context)
        throws Exception {
      return null;
    }
  }

  public static class TestKubernetesDependentResource
      extends KubernetesDependentResource<Deployment, TestCustomResource> {}
}
