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
package io.javaoperatorsdk.operator.workflow.manageddependentdeletecondition;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ManagedDependentDeleteConditionIT {

  public static final String RESOURCE_NAME = "test1";
  public static final String CUSTOM_FINALIZER = "test/customfinalizer";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(o -> o.withDefaultNonSSAResource(Set.of()))
          .withReconciler(new ManagedDependentDefaultDeleteConditionReconciler())
          .build();

  @Test
  void resourceNotDeletedUntilDependentDeleted() {
    var resource = new ManagedDependentDefaultDeleteConditionCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(RESOURCE_NAME).build());
    resource = extension.create(resource);

    await()
        .timeout(Duration.ofSeconds(300))
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
              var sec = extension.get(Secret.class, RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(sec).isNotNull();
            });

    var secret = extension.get(Secret.class, RESOURCE_NAME);
    secret.getMetadata().getFinalizers().add(CUSTOM_FINALIZER);
    secret = extension.replace(secret);

    extension.delete(resource);

    // both resources are present until the finalizer removed
    await()
        .pollDelay(Duration.ofMillis(250))
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
              var sec = extension.get(Secret.class, RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(sec).isNotNull();
            });

    secret.getMetadata().getFinalizers().clear();
    extension.replace(secret);

    await()
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
              var sec = extension.get(Secret.class, RESOURCE_NAME);
              assertThat(cm).isNull();
              assertThat(sec).isNull();
            });
  }
}
