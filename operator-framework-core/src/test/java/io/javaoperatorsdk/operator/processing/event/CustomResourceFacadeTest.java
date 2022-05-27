package io.javaoperatorsdk.operator.processing.event;

import org.junit.jupiter.api.BeforeEach;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.mockito.Mockito.mock;

@SuppressWarnings("unchecked")
class CustomResourceFacadeTest {
  private static final String FINALIZER = "javaoperatorsdk.io/finalizer";

  KubernetesClient client = mock(KubernetesClient.class);
  ReconciliationDispatcher.CustomResourceFacade<TestCustomResource> customResourceFacade =
      new ReconciliationDispatcher.CustomResourceFacade<>(mock(MixedOperation.class), client);

  TestCustomResource testCustomResource = TestUtils.testCustomResource();

  @BeforeEach
  void setup() {
    testCustomResource.addFinalizer(FINALIZER);
  }

}
