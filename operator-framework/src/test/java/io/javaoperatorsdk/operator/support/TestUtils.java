package io.javaoperatorsdk.operator.support;

import java.util.HashMap;
import java.util.UUID;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.baseapi.simple.TestCustomResource;
import io.javaoperatorsdk.operator.baseapi.simple.TestCustomResourceSpec;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

public class TestUtils {

  public static final String TEST_CUSTOM_RESOURCE_PREFIX = "test-custom-resource-";
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
            .build());
    resource.getMetadata().setAnnotations(new HashMap<>());
    resource.setKind("CustomService");
    resource.setSpec(new TestCustomResourceSpec());
    resource.getSpec().setConfigMapName("test-config-map");
    resource.getSpec().setKey("test-key");
    resource.getSpec().setValue("test-value");
    return resource;
  }

  public static TestCustomResource testCustomResourceWithPrefix(String id) {
    TestCustomResource resource = new TestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder().withName(TEST_CUSTOM_RESOURCE_PREFIX + id).build());
    resource.setKind("CustomService");
    resource.setSpec(new TestCustomResourceSpec());
    resource.getSpec().setConfigMapName("test-config-map-" + id);
    resource.getSpec().setKey("test-key");
    resource.getSpec().setValue(id);
    return resource;
  }

  public static void waitXms(int x) {
    try {
      Thread.sleep(x);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
  }

  public static int getNumberOfExecutions(LocallyRunOperatorExtension extension) {
    return ((TestExecutionInfoProvider) extension.getReconcilers().get(0)).getNumberOfExecutions();
  }
}
