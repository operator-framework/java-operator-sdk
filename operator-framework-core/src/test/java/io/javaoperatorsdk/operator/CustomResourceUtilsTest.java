package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.sample.simple.NamespacedTestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CustomResourceUtilsTest {
  @Test
  public void assertNamespacedCustomResource() {
    var clusterCrd = TestUtils.testCRD("Cluster");

    CustomResourceUtils.assertCustomResource(TestCustomResource.class, clusterCrd);

    Assertions.assertThrows(
        OperatorException.class,
        () ->
            CustomResourceUtils.assertCustomResource(
                NamespacedTestCustomResource.class, clusterCrd));

    var namespacedCrd = TestUtils.testCRD("Namespaced");

    Assertions.assertThrows(
        OperatorException.class,
        () -> CustomResourceUtils.assertCustomResource(TestCustomResource.class, namespacedCrd));

    CustomResourceUtils.assertCustomResource(NamespacedTestCustomResource.class, namespacedCrd);
  }
}
