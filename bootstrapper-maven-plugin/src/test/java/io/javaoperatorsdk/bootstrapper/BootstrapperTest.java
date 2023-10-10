package io.javaoperatorsdk.bootstrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.boostrapper.Bootstrapper;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapperTest {

  private static final Logger log = LoggerFactory.getLogger(BootstrapperTest.class);

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

      log.info("files: {}", new File(
          "/home/runner/.m2/repository/io/javaoperatorsdk/operator-framework-bom/4.5.0-SNAPSHOT")
          .list());

      var mvnwPath = "./target/test-project/mvnw";

      Files.setPosixFilePermissions(Path.of(mvnwPath), Set.of(PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_EXECUTE));
      var process = Runtime.getRuntime()
          .exec(mvnwPath + " -X clean install -f target/test-project/pom.xml");

      BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getInputStream()));

      log.info("Maven output:");
      String logLine;
      while ((logLine = stdError.readLine()) != null) {
        System.out.println(logLine);
      }
      var res = process.waitFor();
      log.info("exit code: {}", res);
      assertThat(res).isZero();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
