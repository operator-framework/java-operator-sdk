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
package io.javaoperatorsdk.operator;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OperatorKeepAliveTest {

  @Test
  void keepsTheJvmAliveWithANonDaemonThreadUntilStopped() {
    var operator = newOperator();

    operator.keepAlive();

    assertThat(keepAliveThread()).hasValueSatisfying(t -> assertThat(t.isDaemon()).isFalse());

    operator.stop();

    await().untilAsserted(() -> assertThat(keepAliveThread()).isEmpty());
  }

  @Test
  void callingItAgainDoesNotStartAnotherThread() {
    var operator = newOperator();
    try {
      operator.keepAlive();
      operator.keepAlive();

      assertThat(keepAliveThreads()).isEqualTo(1);
    } finally {
      operator.stop();
    }
  }

  @Test
  void canBeStartedAgainAfterTheOperatorWasStopped() {
    var operator = newOperator();
    operator.keepAlive();
    operator.stop();
    await().untilAsserted(() -> assertThat(keepAliveThread()).isEmpty());

    operator.keepAlive();
    try {
      assertThat(keepAliveThread()).isPresent();
    } finally {
      operator.stop();
    }
  }

  @Test
  void noThreadIsStartedUnlessRequested() {
    var operator = newOperator();
    try {
      assertThat(keepAliveThread()).isEmpty();
    } finally {
      operator.stop();
    }
  }

  private static Operator newOperator() {
    return new Operator(MockKubernetesClient.client(TestCustomResource.class));
  }

  private static long keepAliveThreads() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.getName().equals("josdk-keep-alive") && t.isAlive())
        .count();
  }

  private static Optional<Thread> keepAliveThread() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(t -> t.getName().equals("josdk-keep-alive") && t.isAlive())
        .findFirst();
  }
}
