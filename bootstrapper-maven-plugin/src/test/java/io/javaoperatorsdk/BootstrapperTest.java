package io.javaoperatorsdk;

import java.io.File;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapperTest {

  Bootstrapper bootstrapper = new Bootstrapper();

  @Test
  void copiesFilesToTarget() {
    bootstrapper.create(new File("target"), "io.sample", "test-project");

    var targetDir = new File("target", "test-project");
    assertThat(targetDir.list()).contains("pom.xml");
  }

}
