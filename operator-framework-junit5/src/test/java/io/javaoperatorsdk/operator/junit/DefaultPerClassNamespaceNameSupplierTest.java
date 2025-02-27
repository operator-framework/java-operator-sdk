package io.javaoperatorsdk.operator.junit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import static io.javaoperatorsdk.operator.junit.AbstractOperatorExtension.MAX_NAMESPACE_NAME_LENGTH;
import static io.javaoperatorsdk.operator.junit.DefaultNamespaceNameSupplier.DELIMITER;
import static io.javaoperatorsdk.operator.junit.DefaultNamespaceNameSupplier.RANDOM_SUFFIX_LENGTH;
import static io.javaoperatorsdk.operator.junit.DefaultPerClassNamespaceNameSupplier.MAX_CLASS_NAME_LENGTH;
import static io.javaoperatorsdk.operator.junit.NamespaceNamingTestUtils.SHORT_CLASS_NAME;
import static io.javaoperatorsdk.operator.junit.NamespaceNamingTestUtils.VERY_LONG_CLASS_NAME;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultPerClassNamespaceNameSupplierTest {

  DefaultPerClassNamespaceNameSupplier supplier = new DefaultPerClassNamespaceNameSupplier();

  @Test
  void shortClassCase() {
    var ns = supplier.apply(mockExtensionContext(SHORT_CLASS_NAME));

    assertThat(ns).startsWith(SHORT_CLASS_NAME + DELIMITER);
    shortEnoughAndEndsWithRandomString(ns);
  }

  @Test
  void longClassCase() {
    var ns = supplier.apply(mockExtensionContext(VERY_LONG_CLASS_NAME));

    assertThat(ns).startsWith(VERY_LONG_CLASS_NAME.substring(0, MAX_CLASS_NAME_LENGTH) + DELIMITER);
    shortEnoughAndEndsWithRandomString(ns);
    assertThat(ns).hasSize(MAX_NAMESPACE_NAME_LENGTH);
  }

  public static ExtensionContext mockExtensionContext(String className) {
    return NamespaceNamingTestUtils.mockExtensionContext(className, null);
  }

  private static void shortEnoughAndEndsWithRandomString(String ns) {
    assertThat(ns.length()).isLessThanOrEqualTo(MAX_NAMESPACE_NAME_LENGTH);
    assertThat(ns.split("-")[1]).hasSize(RANDOM_SUFFIX_LENGTH);
  }
}
