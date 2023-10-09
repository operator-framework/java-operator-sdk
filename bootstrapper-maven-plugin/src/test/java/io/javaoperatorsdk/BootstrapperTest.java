package io.javaoperatorsdk;

import java.io.File;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BootstrapperTest {

  Bootstrapper bootstrapper = new Bootstrapper();

  @Test
  void copiesFilesToTarget() {
    bootstrapper.create(new File("target"), "io.sample", "test-project");
  }


}
