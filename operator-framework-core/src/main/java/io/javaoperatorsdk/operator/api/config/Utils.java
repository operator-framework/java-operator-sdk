package io.javaoperatorsdk.operator.api.config;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
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
      builtTime = DateFormat.getDateTimeInstance().parse(properties.getProperty("git.build.time"));
    } catch (ParseException e) {
      builtTime = Date.from(Instant.EPOCH);
    }
    return new Version(
        properties.getProperty("git.build.version", "unknown"),
        properties.getProperty("git.commit.id.abbrev", "unknown"),
        builtTime);
  }
}
