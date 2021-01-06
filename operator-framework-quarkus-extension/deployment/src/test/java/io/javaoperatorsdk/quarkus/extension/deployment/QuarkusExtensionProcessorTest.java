package io.javaoperatorsdk.quarkus.extension.deployment;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkus.test.QuarkusProdModeTest;
import javax.inject.Inject;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class QuarkusExtensionProcessorTest {

  @RegisterExtension
  static final QuarkusProdModeTest config =
      new QuarkusProdModeTest()
          .setArchiveProducer(
              () -> ShrinkWrap.create(JavaArchive.class).addClasses(TestController.class))
          .setApplicationName("basic-app")
          .setApplicationVersion("0.1-SNAPSHOT");

  @Inject TestController controller;
  @Inject ConfigurationService configurationService;

  @Test
  void test() {
    assertNotNull(controller);
    assertNotNull(configurationService);
    assertNotNull(configurationService.getConfigurationFor(controller));
  }
}
