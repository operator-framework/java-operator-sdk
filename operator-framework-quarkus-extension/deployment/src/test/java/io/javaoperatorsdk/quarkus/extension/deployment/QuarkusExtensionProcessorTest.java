package io.javaoperatorsdk.quarkus.extension.deployment;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkus.test.QuarkusUnitTest;
import javax.inject.Inject;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class QuarkusExtensionProcessorTest {

  @RegisterExtension
  static final QuarkusUnitTest config =
      new QuarkusUnitTest().setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class));

  @Inject TestController controller;
  @Inject ConfigurationService configurationService;

  @Test
  void test() {
    assertNotNull(controller);
    assertNotNull(configurationService);
    assertNotNull(configurationService.getConfigurationFor(controller));
  }
}
