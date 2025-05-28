package io.javaoperatorsdk.operator.junit;

import org.junit.jupiter.api.Test;

import static io.javaoperatorsdk.operator.junit.AbstractOperatorExtension.MAX_NAMESPACE_NAME_LENGTH;
import static io.javaoperatorsdk.operator.junit.DefaultNamespaceNameSupplier.*;
import static io.javaoperatorsdk.operator.junit.NamespaceNamingTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultNamespaceNameSupplierTest {

  DefaultNamespaceNameSupplier supplier = new DefaultNamespaceNameSupplier();

  @Test
  void trivialCase() {
    String ns = supplier.apply(mockExtensionContext(SHORT_CLASS_NAME, SHORT_METHOD_NAME));

    assertThat(ns).startsWith(SHORT_CLASS_NAME + DELIMITER + SHORT_METHOD_NAME + DELIMITER);
    shortEnoughAndEndsWithRandomString(ns);
  }

  @Test
  void classPartLongerCase() {
    String ns = supplier.apply(mockExtensionContext(LONG_CLASS_NAME, SHORT_METHOD_NAME));

    assertThat(ns).startsWith(LONG_CLASS_NAME + DELIMITER + SHORT_METHOD_NAME + DELIMITER);
    shortEnoughAndEndsWithRandomString(ns);
  }

  @Test
  void methodPartLonger() {
    String ns = supplier.apply(mockExtensionContext(SHORT_CLASS_NAME, LONG_METHOD_NAME));

    assertThat(ns).startsWith(SHORT_CLASS_NAME + DELIMITER + LONG_METHOD_NAME + DELIMITER);
    shortEnoughAndEndsWithRandomString(ns);
  }

  @Test
  void methodPartAndClassPartLonger() {
    String ns = supplier.apply(mockExtensionContext(LONG_CLASS_NAME, LONG_METHOD_NAME));

    assertThat(ns)
        .startsWith(
            LONG_CLASS_NAME.substring(0, PART_RESERVED_NAME_LENGTH)
                + DELIMITER
                + LONG_METHOD_NAME.substring(0, PART_RESERVED_NAME_LENGTH)
                + DELIMITER);
    shortEnoughAndEndsWithRandomString(ns);
  }

  private static void shortEnoughAndEndsWithRandomString(String ns) {
    assertThat(ns.length()).isLessThanOrEqualTo(MAX_NAMESPACE_NAME_LENGTH);
    assertThat(ns.split("-")[2]).hasSize(RANDOM_SUFFIX_LENGTH);
  }
}
