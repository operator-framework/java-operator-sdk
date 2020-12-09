package io.javaoperatorsdk.operator.springboot.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class AutoConfigurationTest {

  @Autowired private RetryProperties retryProperties;

  @Autowired private OperatorProperties operatorProperties;

  @MockBean private Operator operator;

  @Autowired private KubernetesClient kubernetesClient;

  @Autowired private List<ResourceController> resourceControllers;

  @Test
  public void loadsKubernetesClientPropertiesProperly() {
    assertEquals("user", operatorProperties.getUsername());
    assertEquals("password", operatorProperties.getPassword());
    assertEquals("http://master.url", operatorProperties.getMasterUrl());
  }

  @Test
  public void loadsRetryPropertiesProperly() {
    assertEquals(3, retryProperties.getMaxAttempts().intValue());
    assertEquals(1000, retryProperties.getInitialInterval().intValue());
    assertEquals(1.5, retryProperties.getIntervalMultiplier().doubleValue());
    assertEquals(50000, retryProperties.getMaxInterval().intValue());
    assertEquals(100000, retryProperties.getMaxElapsedTime().intValue());
  }

  @Test
  public void beansCreated() {
    assertNotNull(kubernetesClient);
  }

  @Test
  public void resourceControllersAreDiscovered() {
    assertEquals(1, resourceControllers.size());
    assertTrue(resourceControllers.get(0) instanceof TestController);
  }
}
