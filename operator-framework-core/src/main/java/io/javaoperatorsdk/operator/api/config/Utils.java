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
package io.javaoperatorsdk.operator.api.config;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class Utils {

  private static final Logger log = LoggerFactory.getLogger(Utils.class);
  public static final String CHECK_CRD_ENV_KEY = "JAVA_OPERATOR_SDK_CHECK_CRD";
  public static final String DEBUG_THREAD_POOL_ENV_KEY = "JAVA_OPERATOR_SDK_DEBUG_THREAD_POOL";
  public static final String USE_MDC_ENV_KEY = "JAVA_OPERATOR_SDK_USE_MDC";
  public static final String GENERIC_PARAMETER_TYPE_ERROR_PREFIX =
      "Couldn't retrieve generic parameter type from ";

  public static final Version VERSION = loadFromProperties();

  /**
   * Attempts to load version information from a properties file produced at build time, currently
   * via the {@code git-commit-id-plugin} maven plugin.
   *
   * @return a {@link Version} object encapsulating the version information
   */
  private static Version loadFromProperties() {
    final var is =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("version.properties");

    final var properties = new Properties();
    if (is != null) {
      try {
        properties.load(is);
      } catch (IOException e) {
        log.warn("Couldn't load version information: {}", e.getMessage());
      }
    } else {
      log.warn("Couldn't find version.properties file. Default version information will be used.");
    }

    Date builtTime;
    try {
      String time = properties.getProperty("git.build.time");
      if (time != null) {
        builtTime = Date.from(Instant.parse(time));
      } else {
        builtTime = Date.from(Instant.EPOCH);
      }
    } catch (Exception e) {
      log.debug("Couldn't parse git.build.time property", e);
      builtTime = Date.from(Instant.EPOCH);
    }
    return new Version(properties.getProperty("git.commit.id.abbrev", "unknown"), builtTime);
  }

  public static int ensureValid(int value, String description, int minValue) {
    return ensureValid(value, description, minValue, minValue);
  }

  public static int ensureValid(int value, String description, int minValue, int defaultValue) {
    if (value < minValue) {
      if (defaultValue < minValue) {
        throw new IllegalArgumentException(
            "Default value for " + description + " must be greater than " + minValue);
      }
      log.warn(
          "Requested {} should be greater than {}. Requested: {}, using {}{} instead",
          description,
          minValue,
          value,
          defaultValue,
          defaultValue == minValue ? "" : " (default)");
      value = defaultValue;
    }
    return value;
  }

  @SuppressWarnings("unused")
  // this is used in the Quarkus extension
  public static boolean isValidateCustomResourcesEnvVarSet() {
    return System.getProperty(CHECK_CRD_ENV_KEY) != null;
  }

  public static boolean shouldCheckCRDAndValidateLocalModel() {
    return getBooleanFromSystemPropsOrDefault(CHECK_CRD_ENV_KEY, false);
  }

  public static boolean debugThreadPool() {
    return getBooleanFromSystemPropsOrDefault(DEBUG_THREAD_POOL_ENV_KEY, false);
  }

  public static boolean getBooleanFromSystemPropsOrDefault(
      String propertyName, boolean defaultValue) {
    var property = System.getProperty(propertyName);
    if (property == null) {
      return defaultValue;
    } else {
      property = property.trim().toLowerCase();
      return switch (property) {
        case "true" -> true;
        case "false" -> false;
        default -> defaultValue;
      };
    }
  }

  public static Class<?> getFirstTypeArgumentFromExtendedClass(Class<?> clazz) {
    return getTypeArgumentFromExtendedClassByIndex(clazz, 0);
  }

  public static Class<?> getTypeArgumentFromExtendedClassByIndex(Class<?> clazz, int index) {
    try {
      Type type = clazz.getGenericSuperclass();
      return (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[index];
    } catch (Exception e) {
      throw new RuntimeException(
          GENERIC_PARAMETER_TYPE_ERROR_PREFIX
              + clazz.getSimpleName()
              + " because it doesn't extend a class that is parameterized with the type we want to"
              + " retrieve",
          e);
    }
  }

  public static Class<?> getTypeArgumentFromHierarchyByIndex(Class<?> clazz, int index) {
    return getTypeArgumentFromHierarchyByIndex(clazz, null, index);
  }

  public static Class<?> getTypeArgumentFromHierarchyByIndex(
      Class<?> clazz, Class<?> expectedImplementedInterface, int index) {
    Class<?> c = clazz;
    while (!(c.getGenericSuperclass() instanceof ParameterizedType)) {
      c = c.getSuperclass();
    }
    Class<?> actualTypeArgument =
        (Class<?>) ((ParameterizedType) c.getGenericSuperclass()).getActualTypeArguments()[index];
    if (expectedImplementedInterface != null
        && !expectedImplementedInterface.isAssignableFrom(actualTypeArgument)) {
      throw new IllegalArgumentException(
          GENERIC_PARAMETER_TYPE_ERROR_PREFIX
              + clazz.getName()
              + "because it doesn't extend a class that is parametrized with the type that"
              + " implements "
              + expectedImplementedInterface.getSimpleName()
              + ". Please provide the resource type in the constructor (e.g.,"
              + " super(Deployment.class).");
    } else if (expectedImplementedInterface == null && actualTypeArgument.equals(Object.class)) {
      throw new IllegalArgumentException(
          GENERIC_PARAMETER_TYPE_ERROR_PREFIX
              + clazz.getName()
              + " because it doesn't extend a class that is parametrized with the type we want to"
              + " retrieve or because it's Object.class. Please provide the resource type in the "
              + "constructor (e.g., super(Deployment.class).");
    }
    return actualTypeArgument;
  }

  public static Class<?> getFirstTypeArgumentFromInterface(
      Class<?> clazz, Class<?> expectedImplementedInterface) {
    return getTypeArgumentFromInterfaceByIndex(clazz, expectedImplementedInterface, 0);
  }

  public static Class<?> getTypeArgumentFromInterfaceByIndex(
      Class<?> clazz, Class<?> expectedImplementedInterface, int index) {
    if (expectedImplementedInterface.isAssignableFrom(clazz)) {
      final var genericInterfaces = clazz.getGenericInterfaces();

      var target = extractType(clazz, expectedImplementedInterface, index, genericInterfaces);
      if (target.isPresent()) {
        return target.get();
      }

      // try the parent if we didn't find a parameter type on the current class
      var parent = clazz.getSuperclass();
      if (!Object.class.equals(parent)) {
        return getTypeArgumentFromInterfaceByIndex(parent, expectedImplementedInterface, index);
      }
    }
    throw new IllegalArgumentException(
        GENERIC_PARAMETER_TYPE_ERROR_PREFIX
            + clazz.getSimpleName()
            + " because it or its superclasses don't implement "
            + expectedImplementedInterface.getSimpleName());
  }

  private static Optional<? extends Class<?>> extractType(
      Class<?> clazz, Class<?> expectedImplementedInterface, int index, Type[] genericInterfaces) {
    Optional<? extends Class<?>> target = Optional.empty();
    if (genericInterfaces.length > 0) {
      // try to find the target interface among them
      target =
          Arrays.stream(genericInterfaces)
              .filter(
                  type ->
                      type.getTypeName().startsWith(expectedImplementedInterface.getName())
                          && type instanceof ParameterizedType)
              .map(ParameterizedType.class::cast)
              .findFirst()
              .map(
                  t -> {
                    final Type argument = t.getActualTypeArguments()[index];
                    if (argument instanceof Class) {
                      return (Class<?>) argument;
                    }
                    // account for the case where the argument itself has parameters, which we will
                    // ignore
                    // and just return the raw type
                    if (argument instanceof ParameterizedType) {
                      final var rawType = ((ParameterizedType) argument).getRawType();
                      if (rawType instanceof Class) {
                        return (Class<?>) rawType;
                      }
                    }
                    throw new IllegalArgumentException(
                        clazz.getSimpleName()
                            + " implements "
                            + expectedImplementedInterface.getSimpleName()
                            + " but indirectly. Java type erasure doesn't allow to retrieve the"
                            + " generic type from it. Retrieved type was: "
                            + argument);
                  });
    }
    return target;
  }

  public static Class<?> getFirstTypeArgumentFromSuperClassOrInterface(
      Class<?> clazz, Class<?> expectedImplementedInterface) {
    return getTypeArgumentFromSuperClassOrInterfaceByIndex(clazz, expectedImplementedInterface, 0);
  }

  public static Class<?> getTypeArgumentFromSuperClassOrInterfaceByIndex(
      Class<?> clazz, Class<?> expectedImplementedInterface, int index) {
    // first check super class if it exists
    try {
      final Class<?> superclass = clazz.getSuperclass();
      if (!superclass.equals(Object.class)) {
        try {
          return getTypeArgumentFromExtendedClassByIndex(clazz, index);
        } catch (Exception e) {
          // try interfaces
          try {
            return getTypeArgumentFromInterfaceByIndex(clazz, expectedImplementedInterface, index);
          } catch (Exception ex) {
            // try on the parent
            return getTypeArgumentFromSuperClassOrInterfaceByIndex(
                superclass, expectedImplementedInterface, index);
          }
        }
      }
      return getTypeArgumentFromInterfaceByIndex(clazz, expectedImplementedInterface, index);
    } catch (Exception e) {
      throw new OperatorException(GENERIC_PARAMETER_TYPE_ERROR_PREFIX + clazz.getSimpleName(), e);
    }
  }

  public static <T> T instantiateAndConfigureIfNeeded(
      Class<? extends T> targetClass,
      Class<T> expectedType,
      String context,
      Configurator<T> configurator) {
    // if class to instantiate equals the expected interface, we cannot instantiate it so just
    // return null as it means we passed on void-type default value
    if (expectedType.equals(targetClass)) {
      return null;
    }

    try {
      final var instance = getConstructor(targetClass).newInstance();

      if (configurator != null) {
        configurator.configure(instance);
      }

      return instance;
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | IllegalStateException e) {
      throw new OperatorException(
          "Couldn't instantiate "
              + expectedType.getSimpleName()
              + " '"
              + targetClass.getName()
              + "'."
              + (context != null ? " Context: " + context : ""),
          e);
    }
  }

  public static <T> Constructor<T> getConstructor(Class<T> targetClass) {
    final Constructor<T> constructor;
    try {
      constructor = targetClass.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          "Couldn't find a no-arg constructor for " + targetClass.getName(), e);
    }
    constructor.setAccessible(true);
    return constructor;
  }

  public static <T> T instantiate(
      Class<? extends T> toInstantiate, Class<T> expectedType, String context) {
    return instantiateAndConfigureIfNeeded(toInstantiate, expectedType, context, null);
  }

  @FunctionalInterface
  public interface Configurator<T> {
    void configure(T instance);
  }

  @SuppressWarnings("rawtypes")
  public static String contextFor(
      ControllerConfiguration<?> controllerConfiguration,
      Class<? extends DependentResource> dependentType,
      Class<? extends Annotation> configurationAnnotation) {
    return contextFor(controllerConfiguration.getName(), dependentType, configurationAnnotation);
  }

  public static String contextFor(String reconcilerName) {
    return contextFor(reconcilerName, null, null);
  }

  @SuppressWarnings("rawtypes")
  public static String contextFor(
      String reconcilerName,
      Class<? extends DependentResource> dependentType,
      Class<? extends Annotation> configurationAnnotation) {
    final var annotationName =
        configurationAnnotation != null
            ? configurationAnnotation.getSimpleName()
            : io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.class
                .getSimpleName();
    var context = "annotation: " + annotationName + ", ";
    if (dependentType != null) {
      context += "DependentResource: " + dependentType.getName() + ", ";
    }
    context += "reconciler: " + reconcilerName;

    return context;
  }
}
