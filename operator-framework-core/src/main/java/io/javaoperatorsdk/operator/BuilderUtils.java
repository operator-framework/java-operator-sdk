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
