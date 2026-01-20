/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator;

import java.util.HashMap;
import java.util.UUID;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceSpec;

public class TestUtils {

  public static TestCustomResource testCustomResource() {
    return testCustomResource(new ResourceID(UUID.randomUUID().toString(), "test"));
  }

  public static TestCustomResource testCustomResource1() {
    return testCustomResource(new ResourceID("test1", "default"));
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

  public static ResourceID testCustomResource1Id() {
    return new ResourceID("test1", "default");
  }

  public static TestCustomResource testCustomResource(ResourceID id) {
    TestCustomResource resource = new TestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(id.getName())
            .withResourceVersion("1")
            .withGeneration(1L)
            .withNamespace(id.getNamespace().orElse(null))
            .build());
    resource.getMetadata().setAnnotations(new HashMap<>());
    resource.setSpec(new TestCustomResourceSpec());
    resource.getSpec().setConfigMapName("test-config-map");
    resource.getSpec().setKey("test-key");
    resource.getSpec().setValue("test-value");
    return resource;
  }

  public static <T extends HasMetadata> T markForDeletion(T customResource) {
    customResource.getMetadata().setDeletionTimestamp("2019-8-10");
    return customResource;
  }
}
