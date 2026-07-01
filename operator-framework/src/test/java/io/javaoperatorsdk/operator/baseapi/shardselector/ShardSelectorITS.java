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
package io.javaoperatorsdk.operator.baseapi.shardselector;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubeapitest.junit.EnableKubeAPIServer;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Shard Selector for Splitting Resources Across Operator Instances",
    description =
        """
        Demonstrates how to shard custom resources across multiple operator instances using shard \
        selectors. Two operators watch the same resource type, each configured with a shard \
        selector covering one evenly split half of the UID hash space. The test verifies that a \
        given custom resource is reconciled by exactly one instance, never by both and never by \
        neither. Sharding relies on the Kubernetes API server 'ShardedListAndWatch' alpha feature.
        """)
@EnableKubeAPIServer(
    kubeAPIVersion = "1.36.*",
    apiServerFlags = {"--feature-gates=ShardedListAndWatch=true"},
    updateKubeConfigFile = true)
class ShardSelectorITS {

  // The two selectors split the 64-bit UID hash space in half: [0x0, 0x8000000000000000) and
  // [0x8000000000000000, 0x10000000000000000). Together they cover the whole space with no overlap,
  // so every resource is owned by exactly one shard.
  private static final String SHARD1 =
      "shardRange(object.metadata.uid, '0x0000000000000000', '0x8000000000000000')";
  private static final String SHARD2 =
      "shardRange(object.metadata.uid, '0x8000000000000000', '0x10000000000000000')";

  private static final String TEST_RESOURCE_NAME = "shard-test1";
  private static final Duration EVENT_SETTLE_WINDOW = Duration.ofMillis(500);

  private final KubernetesClient adminClient = new KubernetesClientBuilder().build();

  private Operator operator1;
  private Operator operator2;
  private ShardSelectorTestReconciler reconciler1;
  private ShardSelectorTestReconciler reconciler2;

  @BeforeEach
  void beforeEach() {
    LocallyRunOperatorExtension.applyCrd(ShardSelectorTestCustomResource.class, adminClient);
    reconciler1 = startOperatorForShard(SHARD1);
    reconciler2 = startOperatorForShard(SHARD2);
  }

  @AfterEach
  void cleanup() {
    if (operator1 != null) {
      operator1.stop();
    }
    if (operator2 != null) {
      operator2.stop();
    }
    adminClient.resource(testCustomResource()).delete();
  }

  @Test
  void onlyOneShardReconcilesTheResource() {
    adminClient.resource(testCustomResource()).create();

    // The condition must hold for the whole settle window: exactly one shard ever reconciles the
    // resource, so the other shard has no chance to (incorrectly) pick it up later.
    await()
        .atMost(Duration.ofSeconds(30))
        .during(EVENT_SETTLE_WINDOW)
        .untilAsserted(
            () -> {
              int executions1 = reconciler1.getNumberOfExecutions();
              int executions2 = reconciler2.getNumberOfExecutions();
              // exactly one shard owns the resource
              assertThat((executions1 == 0) ^ (executions2 == 0)).isTrue();
            });
  }

  private ShardSelectorTestReconciler startOperatorForShard(String shardSelector) {
    var reconciler = new ShardSelectorTestReconciler();
    var operator = new Operator(o -> o.withKubernetesClient(new KubernetesClientBuilder().build()));
    operator.register(reconciler, o -> o.withShardSelector(shardSelector));
    operator.start();
    if (operator1 == null) {
      operator1 = operator;
    } else {
      operator2 = operator;
    }
    return reconciler;
  }

  private ShardSelectorTestCustomResource testCustomResource() {
    var resource = new ShardSelectorTestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return resource;
  }
}
