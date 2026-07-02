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

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * Primary resource that is reconciled. Its desired status value is provided by a {@link
 * ConfigCustomResource} that references it (see {@link TargetReconciler}); when no config
 * references it, a default value is used.
 */
@Group("sample.javaoperatorsdk")
@Version("v1")
@Kind("SecondaryToPrimaryRefTarget")
@ShortNames("s2ptarget")
public class TargetCustomResource extends CustomResource<Void, TargetStatus>
    implements Namespaced {}
