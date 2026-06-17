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

import java.util.Set;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

/**
 * Maps a {@link ConfigCustomResource} (secondary) to the {@link TargetCustomResource}s (primaries)
 * it references via {@code spec.targetNames}. A config can reference multiple targets.
 *
 * <p>The mapper only reports the <em>current</em> references. When the referenced set changes — for
 * example when a subset of the targets is replaced — the framework's primary-to-secondary index
 * reconciles both the newly referenced targets and the ones that are no longer referenced, so a
 * dropped target reverts to its default value. The mapper therefore does not need to know about the
 * previous version of the resource.
 */
public class ConfigToTargetMapper implements SecondaryToPrimaryMapper<ConfigCustomResource> {

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(ConfigCustomResource config) {
    var targetNames = config.getSpec().getTargetNames();
    if (targetNames == null || targetNames.isEmpty()) {
      return Set.of();
    }
    var namespace = config.getMetadata().getNamespace();
    return targetNames.stream()
        .filter(name -> name != null && !name.isBlank())
        .map(name -> new ResourceID(name, namespace))
        .collect(Collectors.toSet());
  }
}
