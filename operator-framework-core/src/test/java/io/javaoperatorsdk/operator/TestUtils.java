package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceSpec;
import java.util.HashMap;
import java.util.UUID;

public class TestUtils {

  public static final String TEST_CUSTOM_RESOURCE_NAME = "test-custom-resource";
  public static final String TEST_NAMESPACE = "java-operator-sdk-int-test";

  public static TestCustomResource testCustomResource() {
    return testCustomResource(UUID.randomUUID().toString());
  }

  public static CustomResourceDefinition testCRD(String scope) {
    return new CustomResourceDefinitionBuilder()
        .editOrNewSpec()
        .withScope(scope)
        .and()
        .editOrNewMetadata()
        .withName("test.operator.javaoperatorsdk.io")
        .and()
        .build();
  }

  public static TestCustomResource testCustomResource(String uid) {
    TestCustomResource resource = new TestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_CUSTOM_RESOURCE_NAME)
            .withUid(uid)
            .withGeneration(1L)
            .withNamespace(TEST_NAMESPACE)
            .build());
    resource.getMetadata().setAnnotations(new HashMap<>());
    resource.setKind("CustomService");
    resource.setSpec(new TestCustomResourceSpec());
    resource.getSpec().setConfigMapName("test-config-map");
    resource.getSpec().setKey("test-key");
    resource.getSpec().setValue("test-value");
    return resource;
  }
}
