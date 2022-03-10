package io.javaoperatorsdk.operator.api.config;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

  @Test
  void shouldCheckCRDAndValidateLocalModelByDefault() {
    assertTrue(Utils.shouldCheckCRDAndValidateLocalModel());
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

  public static class TestKubernetesDependentResource
      extends KubernetesDependentResource<Deployment, TestCustomResource> {

    @Override
    protected Deployment desired(TestCustomResource primary, Context context) {
      return null;
    }
  }
}
