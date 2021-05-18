package io.javaoperatorsdk.operator.api.config;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

  private static final Logger log = LoggerFactory.getLogger(Utils.class);
  public static final String CHECK_CRD_ENV_KEY = "JAVA_OPERATOR_SDK_CHECK_CRD";

  /**
   * Attempts to load version information from a properties file produced at build time, currently
   * via the {@code git-commit-id-plugin} maven plugin.
   *
   * @return a {@link Version} object encapsulating the version information
   */
  public static Version loadFromProperties() {
    final var is = Utils.class.getClassLoader().getResourceAsStream("version.properties");

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
      builtTime =
          // RFC 822 date is the default format used by git-commit-id-plugin
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
              .parse(properties.getProperty("git.build.time"));
    } catch (ParseException e) {
      builtTime = Date.from(Instant.EPOCH);
    }
    return new Version(
        properties.getProperty("git.build.version", "unknown"),
        properties.getProperty("git.commit.id.abbrev", "unknown"),
        builtTime);
  }

  public static boolean isValidateCustomResourcesEnvVarSet() {
    return System.getProperty(CHECK_CRD_ENV_KEY) != null;
  }

  public static boolean shouldCheckCRDAndValidateLocalModel() {
    final var value = System.getProperty(CHECK_CRD_ENV_KEY);
    return value == null || Boolean.getBoolean(value);
  }
}
