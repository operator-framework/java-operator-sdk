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

import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder
import io.fabric8.kubernetes.client.KubernetesClientBuilder
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension
import io.javaoperatorsdk.operator.junit.ClusterDeployedOperatorExtension
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension
import java.io.FileInputStream
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Dual-mode (local and remote) E2E test exercising [ConfigMapCopyReconciler]: creates a
 * [ConfigMapCopy] resource, asserts a [ConfigMap] is created with the message copied from spec,
 * updates the message, and verifies the [ConfigMap] is garbage collected on deletion via the
 * owner reference.
 */
class ConfigMapCopyE2E {

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ConfigMapCopyE2E::class.java)

    const val TEST_RESOURCE_NAME = "test-config-map-copy"
    const val INITIAL_MESSAGE = "hello from kotlin"
    const val UPDATED_MESSAGE = "updated from kotlin"
    const val GARBAGE_COLLECTION_TIMEOUT_SECONDS = 90L

    private val client = KubernetesClientBuilder().build()

    fun isLocal(): Boolean {
      val deployment = System.getProperty("test.deployment")
      val remote = deployment != null && deployment == "remote"
      log.info("Running the operator " + (if (remote) "remotely" else "locally"))
      return !remote
    }
  }

  @RegisterExtension
  val operator: AbstractOperatorExtension =
      if (isLocal())
          LocallyRunOperatorExtension.builder().withReconciler(ConfigMapCopyReconciler()).build()
      else
          ClusterDeployedOperatorExtension.builder()
              .withOperatorDeployment(
                  FileInputStream("k8s/operator.yaml").use { client.load(it).items() })
              .build()

  @Test
  fun copiesMessageFromSpecIntoConfigMap() {
    val resource = operator.create(testResource(INITIAL_MESSAGE))

    await().untilAsserted {
      val updated = operator.get(ConfigMapCopy::class.java, TEST_RESOURCE_NAME)
      assertThat(updated.status).isNotNull()
      assertThat(updated.status.observedMessage).isEqualTo(INITIAL_MESSAGE)

      val configMap =
          operator.get(ConfigMap::class.java, ConfigMapCopyReconciler.configMapName(resource))
      assertThat(configMap).isNotNull()
      assertThat(configMap.data[ConfigMapCopyReconciler.MESSAGE_KEY]).isEqualTo(INITIAL_MESSAGE)
    }

    val toUpdate = operator.get(ConfigMapCopy::class.java, TEST_RESOURCE_NAME)
    toUpdate.spec.message = UPDATED_MESSAGE
    operator.replace(toUpdate)

    await().untilAsserted {
      val configMap =
          operator.get(ConfigMap::class.java, ConfigMapCopyReconciler.configMapName(resource))
      assertThat(configMap.data[ConfigMapCopyReconciler.MESSAGE_KEY]).isEqualTo(UPDATED_MESSAGE)
    }

    operator.delete(toUpdate)

    await()
        .atMost(Duration.ofSeconds(GARBAGE_COLLECTION_TIMEOUT_SECONDS))
        .untilAsserted {
          assertThat(
                  operator.get(ConfigMap::class.java, ConfigMapCopyReconciler.configMapName(resource)))
              .isNull()
        }
  }

  private fun testResource(message: String): ConfigMapCopy {
    val resource = ConfigMapCopy()
    resource.metadata =
        ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).withNamespace(operator.namespace).build()
    resource.spec = ConfigMapCopySpec().apply { this.message = message }
    return resource
  }
}
