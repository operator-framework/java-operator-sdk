package io.javaoperatorsdk.operator.junit;

import java.lang.reflect.Method;

import org.junit.jupiter.api.extension.ExtensionContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NamespaceNamingTestUtils {

  public static final String SHORT_CLASS_NAME = Method.class.getSimpleName().toLowerCase();
  public static final String SHORT_METHOD_NAME = "short";
  public static final String LONG_METHOD_NAME = "longmethodnametotestifistruncatedcorrectly";
  public static final String LONG_CLASS_NAME =
      VeryLongClassNameForSakeOfThisTestIfItWorks.class.getSimpleName().toLowerCase();
  // longer then 63
  public static final String VERY_LONG_CLASS_NAME =
      VeryVeryVeryVeryVeryVeryLongClassNameForSakeOfThisTestIfItWorks.class
          .getSimpleName()
          .toLowerCase();

  public static ExtensionContext mockExtensionContext(String className, String methodName) {
    ExtensionContext extensionContext = mock(ExtensionContext.class);
    Method method = mock(Method.class);

    Class clazz;
    if (className.equals(SHORT_CLASS_NAME)) {
      clazz = Method.class;
    } else if (className.equals(LONG_CLASS_NAME)) {
      clazz = VeryLongClassNameForSakeOfThisTestIfItWorks.class;
    } else if (className.equals(VERY_LONG_CLASS_NAME)) {
      clazz = VeryVeryVeryVeryVeryVeryLongClassNameForSakeOfThisTestIfItWorks.class;
    } else {
      throw new IllegalArgumentException();
    }

    when(method.getName()).thenReturn(methodName);
    when(extensionContext.getRequiredTestMethod()).thenReturn(method);
    when(extensionContext.getRequiredTestClass()).thenReturn(clazz);

    return extensionContext;
  }

  public static class VeryVeryVeryVeryVeryVeryLongClassNameForSakeOfThisTestIfItWorks {}

  public static class VeryLongClassNameForSakeOfThisTestIfItWorks {}
}
