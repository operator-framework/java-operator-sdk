package io.javaoperatorsdk.operator.api.config;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
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
    assertThat(Utils.getFirstTypeArgumentFromInterface(TestDependentResource.class))
        .isEqualTo(Deployment.class);
  }

  public static class TestDependentResource
      implements DependentResource<Deployment, TestCustomResource> {

    @Override
    public ReconcileResult<Deployment> reconcile(TestCustomResource primary,
        Context<TestCustomResource> context) {
      return null;
    }

    @Override
    public Optional<Deployment> getResource(TestCustomResource primaryResource) {
      return Optional.empty();
    }

    @Override
    public Class<Deployment> resourceType() {
      return Deployment.class;
    }

  }

  public static class TestKubernetesDependentResource
      extends KubernetesDependentResource<Deployment, TestCustomResource> {

    public TestKubernetesDependentResource() {
      super(Deployment.class);
    }
  }
}
