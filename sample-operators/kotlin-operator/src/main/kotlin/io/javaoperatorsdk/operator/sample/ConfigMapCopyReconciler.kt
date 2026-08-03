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
package io.javaoperatorsdk.operator.sample

import io.fabric8.kubernetes.api.model.ConfigMapBuilder
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl

/**
 * Minimalist reconciler verifying that JOSDK works properly end-to-end when both the custom
 * resource and the reconciler are implemented in Kotlin: it copies `spec.message` into a
 * `ConfigMap`, exercising deserialization of the custom resource by the fabric8 client as well as
 * the rest of the reconciliation runtime.
 */
@ControllerConfiguration
class ConfigMapCopyReconciler : Reconciler<ConfigMapCopy> {

  companion object {
    const val MESSAGE_KEY = "message"

    fun configMapName(resource: ConfigMapCopy): String = resource.metadata.name + "-config"
  }

  override fun reconcile(
      resource: ConfigMapCopy,
      context: Context<ConfigMapCopy>
  ): UpdateControl<ConfigMapCopy> {
    val data: Map<String, String> = mapOf(MESSAGE_KEY to resource.spec.message)
    val configMap =
        ConfigMapBuilder()
            .withMetadata(
                ObjectMetaBuilder()
                    .withName(configMapName(resource))
                    .withNamespace(resource.metadata.namespace)
                    .build())
            .withData<String, String>(data)
            .build()
    configMap.addOwnerReference(resource)

    context.client.resource(configMap).serverSideApply()

    resource.status = ConfigMapCopyStatus()
    resource.status.configMapName = configMap.metadata.name
    resource.status.observedMessage = resource.spec.message

    return UpdateControl.patchStatus(resource)
  }
}
