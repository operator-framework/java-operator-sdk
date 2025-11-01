package io.javaoperatorsdk.bootstrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.boostrapper.Bootstrapper;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapperIT {

  private static final Logger log = LoggerFactory.getLogger(BootstrapperIT.class);

  Bootstrapper bootstrapper = new Bootstrapper();

  @Test
  void copiesFilesToTarget() {
    bootstrapper.create(new File("target"), "io.sample", "test-project");

    var targetDir = new File("target", "test-project");
    assertThat(targetDir.list()).contains("pom.xml");
    assertProjectCompiles();
  }

  private void assertProjectCompiles() {
    try {
      var process =
          Runtime.getRuntime()
              .exec(
                  "mvn clean install -f target/test-project/pom.xml -DskipTests"
                      + " -Dspotless.apply.skip");

      BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));

      log.info("Maven output:");
      String logLine;
      while ((logLine = stdOut.readLine()) != null) {
        log.info(logLine);
      }
      var res = process.waitFor();
      log.info("exit code: {}", res);
      assertThat(res).isZero();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
