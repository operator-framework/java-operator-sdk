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
