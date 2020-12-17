package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceSpec;
import java.util.HashMap;
import java.util.UUID;

public class TestUtils {

  public static final String TEST_CUSTOM_RESOURCE_NAME = "test-custom-resource";

  public static TestCustomResource testCustomResource() {
    return testCustomResource(UUID.randomUUID().toString());
  }

  public static TestCustomResource testCustomResource(String uid) {
    TestCustomResource resource = new TestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_CUSTOM_RESOURCE_NAME)
            .withUid(uid)
            .withGeneration(1L)
            .withNamespace(IntegrationTestSupport.TEST_NAMESPACE)
            .build());
    resource.getMetadata().setAnnotations(new HashMap<>());
    resource.setKind("CustomService");
    resource.setSpec(new TestCustomResourceSpec());
    resource.getSpec().setConfigMapName("test-config-map");
    resource.getSpec().setKey("test-key");
    resource.getSpec().setValue("test-value");
    return resource;
  }

  public static void waitXms(int x) {
    try {
      Thread.sleep(x);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }
}
