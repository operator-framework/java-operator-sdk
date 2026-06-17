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

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

/**
 * Maps a {@link ConfigCustomResource} (secondary) to the {@link TargetCustomResource} (primary) it
 * references via {@code spec.targetName}.
 *
 * <p>The interesting case is handling a <em>reference change</em>: when {@code spec.targetName} is
 * edited to point from one target to another, both targets must be reconciled — the previously
 * referenced one so it can fall back to its default value, and the newly referenced one so it can
 * pick up the config's value. The single-argument mapper only knows about the new reference, so it
 * would only enqueue the new target, leaving the old target with a stale value. By overriding the
 * two-argument variant we additionally enqueue the old target whenever the reference moved.
 */
public class ConfigToTargetMapper implements SecondaryToPrimaryMapper<ConfigCustomResource> {

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(ConfigCustomResource config) {
    var targetName = config.getSpec().getTargetName();
    if (targetName == null || targetName.isBlank()) {
      return Set.of();
    }
    return Set.of(new ResourceID(targetName, config.getMetadata().getNamespace()));
  }
}
