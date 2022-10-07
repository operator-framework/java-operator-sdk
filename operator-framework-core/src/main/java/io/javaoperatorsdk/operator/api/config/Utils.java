package io.javaoperatorsdk.operator.api.config;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
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

  /**
   * Attempts to load version information from a properties file produced at build time, currently
   * via the {@code git-commit-id-plugin} maven plugin.
   *
   * @return a {@link Version} object encapsulating the version information
   */
  public static Version loadFromProperties() {
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
        builtTime =
            // RFC 822 date is the default format used by git-commit-id-plugin
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(time);
      } else {
        builtTime = Date.from(Instant.EPOCH);
      }
    } catch (Exception e) {
      log.debug("Couldn't parse git.build.time property", e);
      builtTime = Date.from(Instant.EPOCH);
    }
    return new Version(
        properties.getProperty("git.build.version", "unknown"),
        properties.getProperty("git.commit.id.abbrev", "unknown"),
        builtTime);
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

  static boolean getBooleanFromSystemPropsOrDefault(String propertyName, boolean defaultValue) {
    var property = System.getProperty(propertyName);
    if (property == null) {
      return defaultValue;
    } else {
      property = property.trim().toLowerCase();
      switch (property) {
        case "true":
          return true;
        case "false":
          return false;
        default:
          return defaultValue;
      }
    }
  }

  public static Class<?> getFirstTypeArgumentFromExtendedClass(Class<?> clazz) {
    try {
      Type type = clazz.getGenericSuperclass();
      return (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
    } catch (Exception e) {
      throw new RuntimeException("Couldn't retrieve generic parameter type from "
          + clazz.getSimpleName()
          + " because it doesn't extend a class that is parameterized with the type we want to retrieve",
          e);
    }
  }

  public static Class<?> getFirstTypeArgumentFromInterface(Class<?> clazz,
      Class<?> expectedImplementedInterface) {
    if (expectedImplementedInterface.isAssignableFrom(clazz)) {
      final var genericInterfaces = clazz.getGenericInterfaces();
      Optional<? extends Class<?>> target = Optional.empty();
      if (genericInterfaces.length > 0) {
        // try to find the target interface among them
        target = Arrays.stream(genericInterfaces)
            .filter(type -> type.getTypeName().startsWith(expectedImplementedInterface.getName())
                && type instanceof ParameterizedType)
            .map(ParameterizedType.class::cast)
            .findFirst()
            .map(t -> {
              final Type argument = t.getActualTypeArguments()[0];
              if (argument instanceof Class) {
                return (Class<?>) argument;
              }
              // account for the case where the argument itself has parameters, which we will ignore
              // and just return the raw type
              if (argument instanceof ParameterizedType) {
                final var rawType = ((ParameterizedType) argument).getRawType();
                if (rawType instanceof Class) {
                  return (Class<?>) rawType;
                }
              }
              throw new IllegalArgumentException(clazz.getSimpleName() + " implements "
                  + expectedImplementedInterface.getSimpleName()
                  + " but indirectly. Java type erasure doesn't allow to retrieve the generic type from it. Retrieved type was: "
                  + argument);
            });
      }

      if (target.isPresent()) {
        return target.get();
      }

      // try the parent
      var parent = clazz.getSuperclass();
      if (!Object.class.equals(parent)) {
        return getFirstTypeArgumentFromInterface(parent, expectedImplementedInterface);
      }
    }
    throw new IllegalArgumentException("Couldn't retrieve generic parameter type from "
        + clazz.getSimpleName() + " because it or its superclasses don't implement "
        + expectedImplementedInterface.getSimpleName());
  }

  public static Class<?> getFirstTypeArgumentFromSuperClassOrInterface(Class<?> clazz,
      Class<?> expectedImplementedInterface) {
    // first check super class if it exists
    try {
      final Class<?> superclass = clazz.getSuperclass();
      if (!superclass.equals(Object.class)) {
        try {
          return getFirstTypeArgumentFromExtendedClass(clazz);
        } catch (Exception e) {
          // try interfaces
          try {
            return getFirstTypeArgumentFromInterface(clazz, expectedImplementedInterface);
          } catch (Exception ex) {
            // try on the parent
            return getFirstTypeArgumentFromSuperClassOrInterface(superclass,
                expectedImplementedInterface);
          }
        }
      }
      return getFirstTypeArgumentFromInterface(clazz, expectedImplementedInterface);
    } catch (Exception e) {
      throw new OperatorException(
          "Couldn't retrieve generic parameter type from " + clazz.getSimpleName(), e);
    }
  }

  public static <T> T instantiateAndConfigureIfNeeded(Class<? extends T> targetClass,
      Class<T> expectedType, String context, Configurator<T> configurator) {
    // if class to instantiate equals the expected interface, we cannot instantiate it so just
    // return null as it means we passed on void-type default value
    if (expectedType.equals(targetClass)) {
      return null;
    }

    try {
      final Constructor<? extends T> constructor = targetClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      final var instance = constructor.newInstance();

      if (configurator != null) {
        configurator.configure(instance);
      }

      return instance;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException
        | NoSuchMethodException e) {
      throw new OperatorException("Couldn't instantiate " + expectedType.getSimpleName() + " '"
          + targetClass.getName() + "': you need to provide an accessible no-arg constructor."
          + (context != null ? " Context: " + context : ""), e);
    }
  }

  public static <T> T instantiate(Class<? extends T> toInstantiate, Class<T> expectedType,
      String context) {
    return instantiateAndConfigureIfNeeded(toInstantiate, expectedType, context, null);
  }

  @FunctionalInterface
  public interface Configurator<T> {
    void configure(T instance);
  }

  @SuppressWarnings("rawtypes")
  public static String contextFor(ControllerConfiguration<?> controllerConfiguration,
      Class<? extends DependentResource> dependentType,
      Class<? extends Annotation> configurationAnnotation) {
    final var annotationName =
        configurationAnnotation != null ? configurationAnnotation.getSimpleName()
            : io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.class
                .getSimpleName();
    var context = "annotation: " + annotationName + ", ";
    if (dependentType != null) {
      context += "DependentResource: " + dependentType.getName() + ", ";
    }
    context += "reconciler: " + controllerConfiguration.getName();


    return context;
  }
}
