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
package io.javaoperatorsdk.operator;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class BuilderUtils {

  // prevent instantiation of util class
  private BuilderUtils() {}

  public static <T, B> B newBuilder(Class<B> builderType, T item) {
    Class<T> builderTargetType = builderTargetType(builderType);
    try {
      Constructor<B> constructor = builderType.getDeclaredConstructor(builderTargetType);
      return constructor.newInstance(item);
    } catch (NoSuchMethodException
        | SecurityException
        | InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException e) {
      throw new OperatorException(
          "Failied to instantiate builder: " + builderType.getCanonicalName() + " using: " + item,
          e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T, B> Class<T> builderTargetType(Class<B> builderType) {
    try {
      Method method = builderType.getDeclaredMethod("build");
      return (Class<T>) method.getReturnType();
    } catch (NoSuchMethodException | SecurityException e) {
      throw new OperatorException(
          "Failied to determine target type for builder: " + builderType.getCanonicalName(), e);
    }
  }
}
