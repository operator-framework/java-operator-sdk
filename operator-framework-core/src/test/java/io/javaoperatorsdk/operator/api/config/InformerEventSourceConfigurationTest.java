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
package io.javaoperatorsdk.operator.api.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InformerEventSourceConfigurationTest {

  @Test
  void updateFromKeepsComparableResourceVersions() {
    // the only caller is KubernetesDependentResource#createEventSource, so dropping it here means
    // the setting silently has no effect on a dependent resource
    var builder = InformerEventSourceConfiguration.from(ConfigMap.class, Secret.class);

    builder.updateFrom(
        InformerConfiguration.builder(ConfigMap.class)
            .withComparableResourceVersions(false)
            .build());

    assertFalse(builder.build().getInformerConfig().isComparableResourceVersions());
  }

  @Test
  void indexersAreEmptyByDefault() {
    var configuration =
        InformerEventSourceConfiguration.from(ConfigMap.class, Secret.class).build();

    assertTrue(configuration.getIndexers().isEmpty());
  }

  @Test
  void builderCollectsIndexers() {
    Function<ConfigMap, List<String>> byName = cm -> List.of(cm.getMetadata().getName());
    Function<ConfigMap, List<String>> byNamespace = cm -> List.of(cm.getMetadata().getNamespace());

    var configuration =
        InformerEventSourceConfiguration.from(ConfigMap.class, Secret.class)
            .withIndexer("name", byName)
            .withIndexers(Map.of("namespace", byNamespace))
            .build();

    assertEquals(Map.of("name", byName, "namespace", byNamespace), configuration.getIndexers());
  }

  @Test
  void configuredIndexersCannotBeModifiedAfterBuild() {
    var configuration =
        InformerEventSourceConfiguration.from(ConfigMap.class, Secret.class)
            .withIndexer("name", cm -> List.of(cm.getMetadata().getName()))
            .build();

    assertThrows(
        UnsupportedOperationException.class,
        () -> configuration.getIndexers().put("other", cm -> List.of()));
  }
}
