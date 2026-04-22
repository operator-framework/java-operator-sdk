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
package io.javaoperatorsdk.operator.baseapi.ownerreferencemultiversion;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionVersion;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test verifying that {@code Mappers.fromOwnerReferences} correctly maps secondary
 * resources back to primary resources even after a CRD version change. The mapper compares only the
 * group part of the apiVersion (ignoring the version), so owner references created under v1 should
 * still work when the CRD storage version switches to v2.
 */
class OwnerRefMultiVersionIT {

  private static final Logger log = LoggerFactory.getLogger(OwnerRefMultiVersionIT.class);

  private static final String CR_NAME = "test-ownerref-mv";
  private static final String CRD_NAME = "ownerrefmultiversioncrs.sample.javaoperatorsdk";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new OwnerRefMultiVersionReconciler())
          .withBeforeStartHook(
              ext -> {
                // The auto-generated CRD has both v1 (storage) and v2. Remove v2 so the
                // cluster initially only knows about v1.
                var client = ext.getKubernetesClient();
                var crd =
                    client
                        .apiextensions()
                        .v1()
                        .customResourceDefinitions()
                        .withName(CRD_NAME)
                        .get();
                if (crd != null) {
                  crd.getSpec().getVersions().removeIf(v -> "v2".equals(v.getName()));
                  crd.getMetadata().setResourceVersion(null);
                  crd.getMetadata().setManagedFields(null);
                  client.resource(crd).serverSideApply();
                  log.info("Applied CRD with v1 only");
                }
              })
          .build();

  @Test
  void mapperWorksAcrossVersionChange() {
    var reconciler = operator.getReconcilerOfType(OwnerRefMultiVersionReconciler.class);

    // 1. Create a v1 custom resource
    var cr = createCR();
    operator.create(cr);

    // 2. Wait for initial reconciliation: ConfigMap is created with owner ref to v1
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              var current = operator.get(OwnerRefMultiVersionCR1.class, CR_NAME);
              assertThat(current.getStatus()).isNotNull();
              assertThat(current.getStatus().getReconcileCount()).isPositive();

              var cm = operator.get(ConfigMap.class, CR_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getMetadata().getOwnerReferences()).hasSize(1);
              assertThat(cm.getMetadata().getOwnerReferences().get(0).getApiVersion())
                  .isEqualTo("sample.javaoperatorsdk/v1");
            });

    int countBeforeUpdate = reconciler.getReconcileCount();
    log.info("Reconcile count before CRD update: {}", countBeforeUpdate);

    // 3. Update CRD to add v2 as the new storage version
    updateCrdWithV2AsStorage(operator.getKubernetesClient());

    // 4. Modify the ConfigMap to trigger the informer event source.
    //    The mapper should still map the ConfigMap (with v1 owner ref) to the primary CR.
    var cm = operator.get(ConfigMap.class, CR_NAME);
    cm.getData().put("updated", "true");
    operator.replace(cm);

    // 5. Verify reconciliation was triggered again
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(300))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getReconcileCount()).isGreaterThan(countBeforeUpdate);
            });

    log.info("Reconcile count after CRD update: {}", reconciler.getReconcileCount());
  }

  private void updateCrdWithV2AsStorage(KubernetesClient client) {
    var crd = client.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME).get();

    // Set v1 to non-storage
    for (var version : crd.getSpec().getVersions()) {
      if ("v1".equals(version.getName())) {
        version.setStorage(false);
      }
    }

    // Add v2 as storage version, reusing v1's schema (specs are compatible)
    var v1 =
        crd.getSpec().getVersions().stream()
            .filter(v -> "v1".equals(v.getName()))
            .findFirst()
            .orElseThrow();

    var v2 = new CustomResourceDefinitionVersion();
    v2.setName("v2");
    v2.setServed(true);
    v2.setStorage(true);
    v2.setSchema(v1.getSchema());
    v2.setSubresources(v1.getSubresources());
    crd.getSpec().getVersions().add(v2);

    crd.getMetadata().setResourceVersion(null);
    crd.getMetadata().setManagedFields(null);
    client.resource(crd).serverSideApply();
    log.info("Updated CRD with v2 as storage version");
  }

  private OwnerRefMultiVersionCR1 createCR() {
    var cr = new OwnerRefMultiVersionCR1();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(CR_NAME);
    cr.setSpec(new OwnerRefMultiVersionSpec());
    cr.getSpec().setValue("initial");
    return cr;
  }
}
