package io.javaoperatorsdk.operator.api.config;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.OperatorException;

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
    return Arrays.stream(clazz.getGenericInterfaces())
        .filter(type -> type.getTypeName().startsWith(expectedImplementedInterface.getName())
            && type instanceof ParameterizedType)
        .map(ParameterizedType.class::cast)
        .findFirst()
        .map(t -> (Class<?>) t.getActualTypeArguments()[0])
        .orElseThrow(() -> new RuntimeException(
            "Couldn't retrieve generic parameter type from " + clazz.getSimpleName()
                + " because it doesn't implement "
                + expectedImplementedInterface.getSimpleName()
                + " directly"));
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
}
