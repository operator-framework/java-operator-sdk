package io.javaoperatorsdk.operator.junit;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.extension.ExtensionContext;

import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;

import static io.javaoperatorsdk.operator.junit.AbstractOperatorExtension.MAX_NAMESPACE_NAME_LENGTH;

public class DefaultNamespaceNameSupplier implements Function<ExtensionContext, String> {

  public static final int RANDOM_SUFFIX_LENGTH = 5;
  public static final int DELIMITERS_LENGTH = 2;

  public static final int MAX_NAME_LENGTH_TOGETHER =
      MAX_NAMESPACE_NAME_LENGTH - DELIMITERS_LENGTH - RANDOM_SUFFIX_LENGTH;
  public static final int PART_RESERVED_NAME_LENGTH = MAX_NAME_LENGTH_TOGETHER / 2;

  public static final String DELIMITER = "-";

  @Override
  public String apply(ExtensionContext context) {
    String classPart = context.getRequiredTestClass().getSimpleName();
    String methodPart = context.getRequiredTestMethod().getName();
    if (classPart.length() + methodPart.length() + DELIMITERS_LENGTH + RANDOM_SUFFIX_LENGTH
        > MAX_NAMESPACE_NAME_LENGTH) {
      if (classPart.length() > PART_RESERVED_NAME_LENGTH) {
        int classPartMaxLength =
            methodPart.length() > PART_RESERVED_NAME_LENGTH
                ? PART_RESERVED_NAME_LENGTH
                : MAX_NAME_LENGTH_TOGETHER - methodPart.length();
        classPart = classPart.substring(0, Math.min(classPartMaxLength, classPart.length()));
      }
      if (methodPart.length() > PART_RESERVED_NAME_LENGTH) {
        int methodPartMaxLength =
            classPart.length() > PART_RESERVED_NAME_LENGTH
                ? PART_RESERVED_NAME_LENGTH
                : MAX_NAME_LENGTH_TOGETHER - classPart.length();
        methodPart = methodPart.substring(0, Math.min(methodPartMaxLength, methodPart.length()));
      }
    }

    String namespace =
        classPart
            + DELIMITER
            + methodPart
            + DELIMITER
            + UUID.randomUUID().toString().substring(0, RANDOM_SUFFIX_LENGTH);
    namespace = KubernetesResourceUtil.sanitizeName(namespace).toLowerCase(Locale.US);
    return namespace;
  }
}
