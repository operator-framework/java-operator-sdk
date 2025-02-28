package io.javaoperatorsdk.operator.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionTest {

  @Test
  void versionShouldReturnTheSameResultFromMavenAndProperties() {
    String versionFromProperties = Utils.VERSION.getSdkVersion();
    String versionFromMaven = Version.UNKNOWN.getSdkVersion();

    assertEquals(versionFromProperties, versionFromMaven);
  }
}
