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
package io.javaoperatorsdk.operator.api.config;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Throwaway audit test: the usual operator main method ends right after {@code operator.start()}
 * (see docs/.../operations/configuration.md and several samples) and relies on the framework's
 * non-daemon executor threads to keep the JVM alive. Virtual threads are always daemon threads, so
 * with virtual threads enabled nothing keeps the JVM running once main returns.
 */
class VirtualThreadsJvmLifetimeTest {

  /** Mimics an operator main: start the executors, run a reconciliation, return from main. */
  public static class OperatorLikeMain {
    public static void main(String[] args) throws Exception {
      final boolean useVirtualThreads = Boolean.parseBoolean(args[0]);
      final var configService =
          ConfigurationService.newOverriddenConfigurationService(
              new BaseConfigurationService(), o -> o.withUseVirtualThreads(useVirtualThreads));
      final var esm = configService.getExecutorServiceManager();
      // what Operator.start() does: start informers on the caching executor ...
      esm.cachingExecutorService().submit(() -> {}).get();
      // ... and a first reconciliation dispatched by the EventProcessor
      esm.reconcileExecutorService().submit(() -> {}).get();
      System.out.println("main returning");
    }
  }

  private static boolean jvmStillAliveAfterMainReturns(boolean useVirtualThreads) throws Exception {
    final var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    final var process =
        new ProcessBuilder(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                OperatorLikeMain.class.getName(),
                Boolean.toString(useVirtualThreads))
            .redirectErrorStream(true)
            .start();
    try {
      final boolean exited = process.waitFor(10, TimeUnit.SECONDS);
      if (exited) {
        final var output = new String(process.getInputStream().readAllBytes());
        // make sure the child exited normally after main returned, not because it crashed
        assertThat(output).contains("main returning");
        assertThat(process.exitValue()).isZero();
      }
      return !exited;
    } finally {
      process.destroyForcibly();
    }
  }

  @Test
  void controlPlatformThreadsKeepTheOperatorJvmAlive() throws Exception {
    assertThat(jvmStillAliveAfterMainReturns(false)).isTrue();
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_21)
  void virtualThreadsKeepTheOperatorJvmAliveToo() throws Exception {
    assertThat(jvmStillAliveAfterMainReturns(true))
        .as("JVM exited right after main returned with useVirtualThreads=true")
        .isTrue();
  }
}
