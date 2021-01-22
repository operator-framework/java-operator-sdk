package io.javaoperatorsdk.operator.api.config;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Properties;

public class Utils {

  public static Version loadFromProperties() {
    final var is =
        Thread.currentThread().getContextClassLoader().getResourceAsStream("version.properties");
    final var properties = new Properties();
    try {
      properties.load(is);
    } catch (IOException e) {
      e.printStackTrace();
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
}
