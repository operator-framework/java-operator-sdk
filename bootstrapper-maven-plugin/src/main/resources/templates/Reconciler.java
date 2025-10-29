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
package {{groupId}};

import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;

import java.util.Map;
import java.util.Optional;

@Workflow(dependents = {@Dependent(type = ConfigMapDependentResource.class)})
public class {{artifactClassId}}Reconciler implements Reconciler<{{artifactClassId}}CustomResource> {

    public UpdateControl<{{artifactClassId}}CustomResource> reconcile({{artifactClassId}}CustomResource primary,
                                                     Context<{{artifactClassId}}CustomResource> context) {

        return UpdateControl.noUpdate();
    }
}
