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
package io.javaoperatorsdk.operator.processing;

/**
 * Provides the identifier for an object that represents resource. This ID is used to select target
 * external resource for a dependent resource from the resources returned by `{@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#getSecondaryResources(Class)}`. But also for
 * {@link ResourceIDMapper} for event sources in external resources.
 *
 * @param <T>
 */
public interface ResourceIDProvider<T> {

  T resourceId();
}
