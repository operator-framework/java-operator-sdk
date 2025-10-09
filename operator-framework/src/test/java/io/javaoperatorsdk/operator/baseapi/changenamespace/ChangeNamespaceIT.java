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
package io.javaoperatorsdk.operator.baseapi.changenamespace;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.RegisteredController;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ChangeNamespaceIT {

  public static final String TEST_RESOURCE_NAME_1 = "test1";
  public static final String TEST_RESOURCE_NAME_2 = "test2";
  public static final String TEST_RESOURCE_NAME_3 = "test3";
  public static final String ADDITIONAL_TEST_NAMESPACE = "additional-test-namespace";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new ChangeNamespaceTestReconciler())
          .build();

  @BeforeEach
  void setup() {
    client().namespaces().resource(additionalTestNamespace()).create();
  }

  @AfterEach
  void cleanup() {
    client().namespaces().resource(additionalTestNamespace()).delete();
  }

  @SuppressWarnings("rawtypes")
  @Test
  void addNewAndRemoveOldNamespaceTest() {
    var reconciler = operator.getReconcilerOfType(ChangeNamespaceTestReconciler.class);
    var defaultNamespaceResource = operator.create(customResource(TEST_RESOURCE_NAME_1));

    assertReconciled(reconciler, defaultNamespaceResource);
    var resourceInAdditionalTestNamespace = createResourceInAdditionalNamespace();

    assertNotReconciled(reconciler, resourceInAdditionalTestNamespace);
    // adding additional namespace
    RegisteredController registeredController =
        operator.getRegisteredControllerForReconcile(ChangeNamespaceTestReconciler.class);
    registeredController.changeNamespaces(
        Set.of(operator.getNamespace(), ADDITIONAL_TEST_NAMESPACE));

    assertReconciled(reconciler, resourceInAdditionalTestNamespace);

    // removing a namespace
    registeredController.changeNamespaces(Set.of(ADDITIONAL_TEST_NAMESPACE));

    var newResourceInDefaultNamespace = operator.create(customResource(TEST_RESOURCE_NAME_3));
    assertNotReconciled(reconciler, newResourceInDefaultNamespace);

    ConfigMap firstMap = operator.get(ConfigMap.class, TEST_RESOURCE_NAME_1);
    firstMap.setData(Map.of("data", "newdata"));
    operator.replace(firstMap);
    assertReconciled(reconciler, defaultNamespaceResource);
  }

  @Test
  void changeToWatchAllNamespaces() {
    var reconciler = operator.getReconcilerOfType(ChangeNamespaceTestReconciler.class);
    var resourceInAdditionalTestNamespace = createResourceInAdditionalNamespace();

    assertNotReconciled(reconciler, resourceInAdditionalTestNamespace);

    var registeredController =
        operator.getRegisteredControllerForReconcile(ChangeNamespaceTestReconciler.class);

    registeredController.changeNamespaces(Set.of(Constants.WATCH_ALL_NAMESPACES));

    assertReconciled(reconciler, resourceInAdditionalTestNamespace);

    registeredController.changeNamespaces(Set.of(operator.getNamespace()));

    var defaultNamespaceResource = operator.create(customResource(TEST_RESOURCE_NAME_1));
    var resource2InAdditionalResource = createResourceInAdditionalNamespace(TEST_RESOURCE_NAME_3);
    assertReconciled(reconciler, defaultNamespaceResource);
    assertNotReconciled(reconciler, resource2InAdditionalResource);
  }

  private static void assertReconciled(
      ChangeNamespaceTestReconciler reconciler,
      ChangeNamespaceTestCustomResource resourceInAdditionalTestNamespace) {
    await()
        .untilAsserted(
            () ->
                assertThat(
                        reconciler.numberOfResourceReconciliations(
                            resourceInAdditionalTestNamespace))
                    .isEqualTo(2));
  }

  private static void assertNotReconciled(
      ChangeNamespaceTestReconciler reconciler,
      ChangeNamespaceTestCustomResource resourceInAdditionalTestNamespace) {
    await()
        .pollDelay(Duration.ofMillis(200))
        .untilAsserted(
            () ->
                assertThat(
                        reconciler.numberOfResourceReconciliations(
                            resourceInAdditionalTestNamespace))
                    .isZero());
  }

  private ChangeNamespaceTestCustomResource createResourceInAdditionalNamespace() {
    return createResourceInAdditionalNamespace(TEST_RESOURCE_NAME_2);
  }

  private ChangeNamespaceTestCustomResource createResourceInAdditionalNamespace(String name) {
    var res = customResource(name);
    return client()
        .resources(ChangeNamespaceTestCustomResource.class)
        .inNamespace(ADDITIONAL_TEST_NAMESPACE)
        .resource(res)
        .create();
  }

  private KubernetesClient client() {
    return operator.getKubernetesClient();
  }

  private Namespace additionalTestNamespace() {
    return new NamespaceBuilder()
        .withMetadata(new ObjectMetaBuilder().withName(ADDITIONAL_TEST_NAMESPACE).build())
        .build();
  }

  private ChangeNamespaceTestCustomResource customResource(String name) {
    ChangeNamespaceTestCustomResource customResource = new ChangeNamespaceTestCustomResource();
    customResource.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return customResource;
  }
}
