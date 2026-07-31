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
package io.javaoperatorsdk.operator.baseapi.informerpool.dynamic;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * The "third" custom resource that is watched as a secondary resource by two different reconcilers.
 * It has no reconciler of its own. One reconciler watches it via a statically registered event
 * source, the other via a dynamically registered one; both are expected to share a single informer
 * for this type from the informer pool.
 */
@Group("sample.javaoperatorsdk")
@Version("v1")
@ShortNames("dsi3")
public class DynamicSharedInformerThirdCustomResource extends CustomResource<Void, Void>
    implements Namespaced {}
