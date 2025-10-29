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
package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import static io.javaoperatorsdk.operator.junit.AbstractOperatorExtension.CRD_READY_WAIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ControllerNamespaceDeletionE2E {

  private static final Logger log = LoggerFactory.getLogger(ControllerNamespaceDeletionE2E.class);

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String INITIAL_VALUE = "initial value";
  public static final String ROLE_ROLE_BINDING_FINALIZER = "controller.deletion/finalizer";
  public static final String RESOURCE_NAME = "operator";

  String namespace;
  KubernetesClient client;

  // not for local mode by design
  @EnabledIfSystemProperty(named = "test.deployment", matches = "remote")
  @Test
  void customResourceCleanedUpOnNamespaceDeletion() {
    deployController();
    client.resource(testResource()).serverSideApply();

    await()
        .untilAsserted(
            () -> {
              var res =
                  client
                      .resources(ControllerNamespaceDeletionCustomResource.class)
                      .inNamespace(namespace)
                      .withName(TEST_RESOURCE_NAME)
                      .get();
              assertThat(res.getStatus()).isNotNull();
              assertThat(res.getStatus().getValue()).isEqualTo(INITIAL_VALUE);
            });

    client.namespaces().withName(namespace).delete();

    await()
        .timeout(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              var ns =
                  client
                      .resources(ControllerNamespaceDeletionCustomResource.class)
                      .inNamespace(namespace)
                      .withName(TEST_RESOURCE_NAME)
                      .get();
              assertThat(ns).isNull();
            });

    log.info("Removing finalizers from role and role bing and service account");
    removeRoleAndRoleBindingFinalizers();

    await()
        .timeout(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              var ns = client.namespaces().withName(namespace).get();
              assertThat(ns).isNull();
            });
  }

  private void removeRoleAndRoleBindingFinalizers() {
    var rolebinding =
        client.rbac().roleBindings().inNamespace(namespace).withName(RESOURCE_NAME).get();
    rolebinding.getFinalizers().clear();
    client.resource(rolebinding).update();

    var role = client.rbac().roles().inNamespace(namespace).withName(RESOURCE_NAME).get();
    role.getFinalizers().clear();
    client.resource(role).update();

    var sa = client.serviceAccounts().inNamespace(namespace).withName(RESOURCE_NAME).get();
    sa.getMetadata().getFinalizers().clear();
    client.resource(sa).update();
  }

  ControllerNamespaceDeletionCustomResource testResource() {
    var cr = new ControllerNamespaceDeletionCustomResource();
    cr.setMetadata(
        new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).withNamespace(namespace).build());
    cr.setSpec(new ControllerNamespaceDeletionSpec());
    cr.getSpec().setValue(INITIAL_VALUE);
    return cr;
  }

  @BeforeEach
  void setup() {
    namespace = "controller-namespace-" + UUID.randomUUID();
    client =
        new KubernetesClientBuilder()
            .withConfig(new ConfigBuilder().withNamespace(namespace).build())
            .build();
    applyCRD();
    client
        .namespaces()
        .resource(
            new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build())
        .create();
  }

  void deployController() {
    try {
      List<HasMetadata> resources = client.load(new FileInputStream("k8s/operator.yaml")).items();
      resources.forEach(
          hm -> {
            hm.getMetadata().setNamespace(namespace);
            if (hm.getKind().equalsIgnoreCase("rolebinding")) {
              var crb = (RoleBinding) hm;
              for (var subject : crb.getSubjects()) {
                subject.setNamespace(namespace);
              }
            }
          });
      client.resourceList(resources).inNamespace(namespace).createOrReplace();

    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  void applyCRD() {
    String path =
        "target/classes/META-INF/fabric8/controllernamespacedeletioncustomresources.namespacedeletion.io-v1.yml";
    try (InputStream is = new FileInputStream(path)) {
      final var crd = client.load(is);
      crd.serverSideApply();
      Thread.sleep(CRD_READY_WAIT);
      log.debug("Applied CRD with name: {}", crd.get().get(0).getMetadata().getName());
    } catch (InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
