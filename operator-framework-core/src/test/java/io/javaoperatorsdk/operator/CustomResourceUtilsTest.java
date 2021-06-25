package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.sample.simple.NamespacedTestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CustomResourceUtilsTest {
  @Test
  public void assertClusterCustomResourceIsCluster() {
    var crd = TestUtils.testCRD("Cluster");

    CustomResourceUtils.assertCustomResource(TestCustomResource.class, crd);
  }

  @Test
  public void assertClusterCustomResourceNotNamespaced() {
    var crd = TestUtils.testCRD("Cluster");

    Assertions.assertThrows(
        OperatorException.class,
        () -> CustomResourceUtils.assertCustomResource(NamespacedTestCustomResource.class, crd));
  }

  @Test
  public void assertNamespacedCustomResourceIsNamespaced() {
    var crd = TestUtils.testCRD("Namespaced");

    CustomResourceUtils.assertCustomResource(NamespacedTestCustomResource.class, crd);
  }

  @Test
  public void assertNamespacedCustomResourceNotCluster() {
    var crd = TestUtils.testCRD("Namespaced");

    Assertions.assertThrows(
        OperatorException.class,
        () -> CustomResourceUtils.assertCustomResource(TestCustomResource.class, crd));
  }
}
