package io.javaoperatorsdk.operator.api.config;

import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;

class VersionTest {

  @Test
  void versionShouldReturnTheSameResultFromMavenAndProperties() {
    String versionFromProperties = Utils.VERSION.getSdkVersion();
    String versionFromMaven = Version.UNKNOWN.getSdkVersion();

    assertEquals(versionFromProperties, versionFromMaven);
  }

}
