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
package io.javaoperatorsdk.operator.migration;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.maven.Assertions.pomXml;

class V53MigrationTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipeFromResources("io.javaoperatorsdk.operator.migration.V5_3Migration");
  }

  @Test
  void renamesJUnitModuleInMaven() {
    rewriteRun(
        pomXml(
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>test</artifactId>
              <version>1.0</version>
              <dependencies>
                <dependency>
                  <groupId>io.javaoperatorsdk</groupId>
                  <artifactId>operator-framework-junit-5</artifactId>
                  <version>5.2.0</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """,
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>test</artifactId>
              <version>1.0</version>
              <dependencies>
                <dependency>
                  <groupId>io.javaoperatorsdk</groupId>
                  <artifactId>operator-framework-junit</artifactId>
                  <version>5.3.0</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
            """));
  }

  @Test
  void renamesMetricsMethods() {
    rewriteRun(
        // language=java
        java(
            """
            package io.javaoperatorsdk.operator.api.monitoring;

            import java.util.Map;

            public interface Metrics {
              default void receivedEvent(Object event, Map<String, Object> metadata) {}
              default void reconcileCustomResource(Object resource, Object retryInfo, Map<String, Object> metadata) {}
              default void reconciliationExecutionStarted(Object resource, Map<String, Object> metadata) {}
              default void reconciliationExecutionFinished(Object resource, Map<String, Object> metadata) {}
              default void failedReconciliation(Object resource, Exception exception, Map<String, Object> metadata) {}
              default void finishedReconciliation(Object resource, Map<String, Object> metadata) {}
              default void cleanupDoneFor(Object resourceID, Map<String, Object> metadata) {}
            }
            """,
            """
            package io.javaoperatorsdk.operator.api.monitoring;

            import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;

            import java.util.Map;

            public interface Metrics {
              default void eventReceived(Object event, Map<String, Object> metadata) {}
              default void reconciliationSubmitted(Object resource, Object retryInfo, Map<String, Object> metadata) {}
              default void reconciliationStarted(Object resource, Map<String, Object> metadata) {}
              default void reconciliationSucceeded(Object resource, Map<String, Object> metadata) {}

                default void reconciliationFailed(Object resource, RetryInfo retryInfo, Exception exception, Map<String, Object> metadata) {}

                default void reconciliationFinished(Object resource, RetryInfo retryInfo, Map<String, Object> metadata) {}
              default void cleanupDone(Object resourceID, Map<String, Object> metadata) {}
            }
            """));
  }

  @Test
  void renamesMetricsMethodCallsInImplementation() {
    rewriteRun(
        // Stub for the Metrics interface
        // language=java
        java(
            """
            package io.javaoperatorsdk.operator.api.monitoring;

            import java.util.Map;

            public interface Metrics {
              default void receivedEvent(Object event, Map<String, Object> metadata) {}
              default void reconcileCustomResource(Object resource, Object retryInfo, Map<String, Object> metadata) {}
            }
            """,
            """
            package io.javaoperatorsdk.operator.api.monitoring;

            import java.util.Map;

            public interface Metrics {
              default void eventReceived(Object event, Map<String, Object> metadata) {}
              default void reconciliationSubmitted(Object resource, Object retryInfo, Map<String, Object> metadata) {}
            }
            """),
        // Implementation that overrides the old method names
        // language=java
        java(
            """
            package com.example;

            import java.util.Map;
            import io.javaoperatorsdk.operator.api.monitoring.Metrics;

            public class MyMetrics implements Metrics {
              @Override
              public void receivedEvent(Object event, Map<String, Object> metadata) {
                System.out.println("event received");
              }

              @Override
              public void reconcileCustomResource(Object resource, Object retryInfo, Map<String, Object> metadata) {
                System.out.println("reconcile");
              }
            }
            """,
            """
            package com.example;

            import java.util.Map;
            import io.javaoperatorsdk.operator.api.monitoring.Metrics;

            public class MyMetrics implements Metrics {
              @Override
              public void eventReceived(Object event, Map<String, Object> metadata) {
                System.out.println("event received");
              }

              @Override
              public void reconciliationSubmitted(Object resource, Object retryInfo, Map<String, Object> metadata) {
                System.out.println("reconcile");
              }
            }
            """));
  }

  @Test
  void removesMonitorSizeOfFromImplementationWithGenerics() {
    rewriteRun(
        // Stub for the Metrics interface (unchanged, from JOSDK library)
        // language=java
        java(
            """
            package io.javaoperatorsdk.operator.api.monitoring;

            import java.util.Map;

            public interface Metrics {
              default void eventReceived(Object event, Map<String, Object> metadata) {}
              default <T extends Map<?, ?>> T monitorSizeOf(T map, String name) { return map; }
            }
            """),
        // Implementation that overrides monitorSizeOf with generic signature
        // language=java
        java(
            """
            package com.example;

            import java.util.Map;
            import io.javaoperatorsdk.operator.api.monitoring.Metrics;

            public class MyMetrics implements Metrics {
              @Override
              public void eventReceived(Object event, Map<String, Object> metadata) {
                System.out.println("event");
              }

              @Override
              public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
                System.out.println("monitoring size");
                return map;
              }
            }
            """,
            """
            package com.example;

            import java.util.Map;
            import io.javaoperatorsdk.operator.api.monitoring.Metrics;

            public class MyMetrics implements Metrics {
              @Override
              public void eventReceived(Object event, Map<String, Object> metadata) {
                System.out.println("event");
              }
            }
            """),
        // Implementation without @Override annotation
        // language=java
        java(
            """
            package com.example;

            import java.util.Map;
            import io.javaoperatorsdk.operator.api.monitoring.Metrics;

            public class AnotherMetrics implements Metrics {
              public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
                return map;
              }
            }
            """,
            """
            package com.example;

            import java.util.Map;
            import io.javaoperatorsdk.operator.api.monitoring.Metrics;

            public class AnotherMetrics implements Metrics {
            }
            """));
  }

  @Test
  void addsRetryInfoParameterToReconciliationFinished() {
    rewriteRun(
        // language=java
        java(
            """
            package io.javaoperatorsdk.operator.api.monitoring;

            import java.util.Map;

            public interface Metrics {
              default void finishedReconciliation(Object resource, Map<String, Object> metadata) {}
            }
            """,
            """
            package io.javaoperatorsdk.operator.api.monitoring;

            import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;

            import java.util.Map;

            public interface Metrics {
                default void reconciliationFinished(Object resource, RetryInfo retryInfo, Map<String, Object> metadata) {}
            }
            """));
  }

  @Test
  void addsNullRetryInfoArgumentToInvocations() {
    rewriteRun(
        // Stub for the Metrics interface with old method names
        // language=java
        java(
            """
            package io.javaoperatorsdk.operator.api.monitoring;

            import java.util.Map;

            public interface Metrics {
              default void failedReconciliation(Object resource, Exception exception, Map<String, Object> metadata) {}
              default void finishedReconciliation(Object resource, Map<String, Object> metadata) {}
            }
            """,
            """
            package io.javaoperatorsdk.operator.api.monitoring;

            import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;

            import java.util.Map;

            public interface Metrics {
                default void reconciliationFailed(Object resource, RetryInfo retryInfo, Exception exception, Map<String, Object> metadata) {}

                default void reconciliationFinished(Object resource, RetryInfo retryInfo, Map<String, Object> metadata) {}
            }
            """),
        // Stub for RetryInfo
        // language=java
        java(
            """
            package io.javaoperatorsdk.operator.api.reconciler;
            public interface RetryInfo {}
            """),
        // Class that calls the old methods
        // language=java
        java(
            """
            package com.example;

            import java.util.Map;
            import io.javaoperatorsdk.operator.api.monitoring.Metrics;

            public class MetricsCaller {
              public void report(Metrics metrics, Object resource, Exception ex, Map<String, Object> meta) {
                metrics.failedReconciliation(resource, ex, meta);
                metrics.finishedReconciliation(resource, meta);
              }
            }
            """,
            """
            package com.example;

            import java.util.Map;
            import io.javaoperatorsdk.operator.api.monitoring.Metrics;

            public class MetricsCaller {
              public void report(Metrics metrics, Object resource, Exception ex, Map<String, Object> meta) {
                metrics.reconciliationFailed(resource, null, ex, meta);
                metrics.reconciliationFinished(resource, null, meta);
              }
            }
            """));
  }

  @Test
  void relocatesResourceActionImport() {
    rewriteRun(
        // Stub for the old ResourceAction location
        // language=java
        java(
            """
            package io.javaoperatorsdk.operator.processing.event.source.controller;

            public class ResourceAction {
            }
            """,
            """
            package io.javaoperatorsdk.operator.processing.event.source;

            public class ResourceAction {
            }
            """),
        // Class that imports ResourceAction from the old package
        // language=java
        java(
            """
            package com.example;

            import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;

            public class MyHandler {
              public void handle(ResourceAction action) {
                System.out.println(action);
              }
            }
            """,
            """
            package com.example;

            import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

            public class MyHandler {
              public void handle(ResourceAction action) {
                System.out.println(action);
              }
            }
            """));
  }
}
