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
package io.javaoperatorsdk.operator.baseapi.secondarytoprimaryreferencechange;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.secondarytoprimaryreferencechange.TargetReconciler.DEFAULT_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Handling a Secondary Resource Whose Reference to a Primary Changes",
    description =
        """
        Demonstrates a configuration custom resource (the secondary) that references a target \
        custom resource (the primary) via a spec field and serves as input for it. The target is \
        reconciled so that, if a config references it, it takes the value from that config; \
        otherwise it falls back to a default. The test shows how to handle the config's reference \
        changing from one target to another: a SecondaryToPrimaryMapper that overrides the \
        two-argument variant enqueues both the previously referenced target (so it reverts to the \
        default) and the newly referenced one (so it picks up the value).
        """)
class SecondaryToPrimaryReferenceChangeIT {

  static final String TARGET_A = "target-a";
  static final String TARGET_B = "target-b";
  static final String CONFIG_NAME = "config";
  static final String CONFIG_VALUE = "value-from-config";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withAdditionalCustomResourceDefinition(ConfigCustomResource.class)
          .withReconciler(new TargetReconciler())
          .build();

  @Test
  void targetTakesValueFromReferencingConfigAndHandlesReferenceChange() {
    operator.create(target(TARGET_A));
    operator.create(target(TARGET_B));

    // With no config, both targets fall back to the default value.
    awaitTargetValue(TARGET_A, DEFAULT_VALUE);
    awaitTargetValue(TARGET_B, DEFAULT_VALUE);

    // A config referencing target A makes A take the config's value; B stays on the default.
    var config = operator.create(config(TARGET_A));
    awaitTargetValue(TARGET_A, CONFIG_VALUE);
    awaitTargetValue(TARGET_B, DEFAULT_VALUE);

    // Moving the reference from A to B reconciles both: A reverts to the default and B picks it up.
    config.getSpec().setTargetName(TARGET_B);
    operator.replace(config);

    awaitTargetValue(TARGET_B, CONFIG_VALUE);
    awaitTargetValue(TARGET_A, DEFAULT_VALUE);
  }

  private void awaitTargetValue(String name, String expectedValue) {
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              var target = operator.get(TargetCustomResource.class, name);
              assertThat(target.getStatus()).isNotNull();
              assertThat(target.getStatus().getValue()).isEqualTo(expectedValue);
            });
  }

  private TargetCustomResource target(String name) {
    var target = new TargetCustomResource();
    target.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return target;
  }

  private ConfigCustomResource config(String targetName) {
    var config = new ConfigCustomResource();
    config.setMetadata(new ObjectMetaBuilder().withName(CONFIG_NAME).build());
    config.setSpec(new ConfigSpec().setTargetName(targetName).setValue(CONFIG_VALUE));
    return config;
  }
}
