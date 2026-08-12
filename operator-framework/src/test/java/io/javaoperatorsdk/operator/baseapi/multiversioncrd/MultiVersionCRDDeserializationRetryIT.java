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
package io.javaoperatorsdk.operator.baseapi.multiversioncrd;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.informers.ExceptionHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.api.config.InformerStoppedHandler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Companion of {@link MultiVersionCRDIT}: there the resource that cannot be deserialized shows up
 * while the informer is already watching, here it is already present when the informer lists the
 * resources on startup. Since {@code stopOnInformerErrorDuringStartup} is {@code false} the
 * operator starts anyway and logs that it will periodically retry the informer, so this test checks
 * whether such a retry really happens, i.e. whether an operator picks it up when the problem is
 * fixed in the cluster while it is running.
 */
@Sample(
    tldr = "Informer Retry After a Custom Resource Deserialization Problem",
    description =
        """
        Shows what happens to an operator whose informer cannot deserialize an already existing \
        custom resource, the situation described in the "Multi Version Custom Resources \
        Deserialization Problem" ADR: a resource created as v2 is stored as v1 because there is \
        no conversion hook, so the reconciler watching v1 receives a String where its spec \
        declares an int. With stopOnInformerErrorDuringStartup set to false the operator still \
        starts, but the informer of the affected controller is stopped for good: the test \
        documents that removing the offending resource while the operator is running does not \
        bring the informer back, the operator has to be restarted.
        """)
class MultiVersionCRDDeserializationRetryIT {

  private static final Logger log =
      LoggerFactory.getLogger(MultiVersionCRDDeserializationRetryIT.class);

  public static final String NOT_DESERIALIZABLE_CR_NAME = "not-deserializable";
  public static final String VALID_CR_NAME = "valid";

  private final CapturingInformerStoppedHandler informerStoppedHandler =
      new CapturingInformerStoppedHandler();

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          // only the reconciler for v1 is registered, it watches the resources without a "version"
          // label, thus also the one created below as v2
          .withReconciler(new MultiVersionCRDTestReconciler1())
          .withConfigurationService(
              overrider ->
                  overrider
                      .withStopOnInformerErrorDuringStartup(false)
                      .withInformerStoppedHandler(informerStoppedHandler))
          // v1 is the stored version and there is no conversion hook, so this resource is stored as
          // it was sent: with a String in the field that v1 declares as an int. The informer of the
          // v1 controller therefore already fails to deserialize it while listing on startup.
          .withBeforeStartHook(extension -> extension.create(notDeserializableResource()))
          .build();

  @Test
  void informerIsNotRetriedAfterTheProblemIsFixedInTheCluster() {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(informerStoppedHandler.getError()).isNotNull());
    assertThat(ExceptionHandler.isDeserializationException(informerStoppedHandler.getError()))
        .isTrue();
    assertThat(operator.getOperator().getRuntimeInfo().allEventSourcesAreHealthy()).isFalse();

    // the problem is fixed while the operator is running: the resource that cannot be deserialized
    // is removed. It is deleted through the v2 endpoint, where it can be deserialized.
    operator.delete(notDeserializableResource());
    await()
        .untilAsserted(
            () ->
                assertThat(
                        operator.get(
                            MultiVersionCRDTestCustomResource2.class, NOT_DESERIALIZABLE_CR_NAME))
                    .isNull());
    operator.create(validResource());

    // Nothing is reconciled: a deserialization error is excluded from the informer retries (see
    // AbstractInformerPool#createInformer) and the Reflector of the fabric8 client completes its
    // stop future as soon as its exception handler declines a retry, so the operator never notices
    // that the cluster is in order again. If this assertion starts to fail because the resource got
    // reconciled, the informer is retried after all: turn the assertions around, that is the
    // behavior we want.
    await()
        .pollDelay(Duration.ofSeconds(5))
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              var actual = operator.get(MultiVersionCRDTestCustomResource1.class, VALID_CR_NAME);
              assertThat(actual).isNotNull();
              assertThat(actual.getStatus()).isNull();
            });
    assertThat(operator.getOperator().getRuntimeInfo().allEventSourcesAreHealthy()).isFalse();
  }

  static MultiVersionCRDTestCustomResource2 notDeserializableResource() {
    var cr = new MultiVersionCRDTestCustomResource2();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(NOT_DESERIALIZABLE_CR_NAME);
    cr.setSpec(new MultiVersionCRDTestCustomResourceSpec2());
    cr.getSpec().setValue("string value");
    return cr;
  }

  static MultiVersionCRDTestCustomResource1 validResource() {
    var cr = new MultiVersionCRDTestCustomResource1();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(VALID_CR_NAME);
    cr.setSpec(new MultiVersionCRDTestCustomResourceSpec1());
    cr.getSpec().setValue(1);
    return cr;
  }

  private static class CapturingInformerStoppedHandler implements InformerStoppedHandler {

    private volatile Throwable error;

    @Override
    @SuppressWarnings("rawtypes")
    public void onStop(SharedIndexInformer informer, Throwable ex) {
      log.info("Informer for {} stopped", informer.getApiTypeClass().getName(), ex);
      error = ex;
    }

    Throwable getError() {
      return error;
    }
  }
}
