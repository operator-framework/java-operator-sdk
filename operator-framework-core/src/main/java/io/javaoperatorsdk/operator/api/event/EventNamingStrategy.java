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
package io.javaoperatorsdk.operator.api.event;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

/**
 * Names the event recorded about an object. The name is what the sink looks a recorded event up by,
 * so it is also the aggregation identity: two records resolving to the same name are counted as
 * occurrences of one event. A name must therefore be unique among the events it should not
 * aggregate with, and stable across operator restarts and replicas.
 *
 * <p>A name must be a valid RFC 1123 DNS subdomain: at most 253 lowercase alphanumeric characters,
 * {@code -} or {@code .}, starting and ending with an alphanumeric character. Names longer than the
 * limit are truncated. A name derived from the object and a fixed lowercase suffix (such as {@code
 * <object>-status-report}) satisfies all of this by construction.
 *
 * <p>An empty result or an invalid name falls back to the default {@code <object>.<identity hash>}
 * name, rather than the event being lost to the API server rejecting the name.
 *
 * <p>Implementations are called from concurrent reconciliations and must be thread safe.
 */
@Experimental(API_MIGHT_CHANGE)
@FunctionalInterface
public interface EventNamingStrategy {

  Optional<String> nameFor(HasMetadata regarding, EventRecord record);

  /** No custom naming: every event gets the default {@code <object>.<identity hash>} name. */
  static EventNamingStrategy none() {
    return (regarding, record) -> Optional.empty();
  }
}
