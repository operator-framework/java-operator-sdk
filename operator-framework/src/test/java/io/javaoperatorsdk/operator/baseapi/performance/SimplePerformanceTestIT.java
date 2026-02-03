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
package io.javaoperatorsdk.operator.baseapi.performance;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.vertx.core.impl.ConcurrentHashSet;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SimplePerformanceTestIT {
  ObjectMapper objectMapper = new ObjectMapper();
  private static final Logger log = LoggerFactory.getLogger(SimplePerformanceTestIT.class);
  public static final String INITIAL_VALUE = "initialValue";
  public static final String RESOURCE_NAME_PREFIX = "resource";
  public static final String INDEX = "index";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new SimplePerformanceTestReconciler())
          .build();

  final int WARM_UP_RESOURCE_NUMBER = 10;
  final int TEST_RESOURCE_NUMBER = 150;

  ExecutorService executor = Executors.newFixedThreadPool(TEST_RESOURCE_NUMBER);

  @Test
  void simpleNaivePerformanceTest() {
    var processors = Runtime.getRuntime().availableProcessors();
    long maxMemory = Runtime.getRuntime().maxMemory();
    log.info("Running performance test with memory: {} and processors: {}", maxMemory, processors);

    var primaryInformer =
        extension
            .getKubernetesClient()
            .resources(SimplePerformanceTestResource.class)
            .inNamespace(extension.getNamespace())
            .inform();

    var statusChecker =
        new StatusChecker(INITIAL_VALUE, 0, WARM_UP_RESOURCE_NUMBER, primaryInformer);
    createResources(0, WARM_UP_RESOURCE_NUMBER, INITIAL_VALUE);
    statusChecker.waitUntilAllInStatus();

    long startTime = System.currentTimeMillis();
    statusChecker =
        new StatusChecker(
            INITIAL_VALUE, WARM_UP_RESOURCE_NUMBER, TEST_RESOURCE_NUMBER, primaryInformer);
    createResources(WARM_UP_RESOURCE_NUMBER, TEST_RESOURCE_NUMBER, INITIAL_VALUE);
    statusChecker.waitUntilAllInStatus();
    var duration = System.currentTimeMillis() - startTime;

    log.info("Create duration: {}", duration);
    saveResults(duration);
  }

  private void saveResults(long duration) {
    try {
      var result = new PerformanceTestResult();
      getRunProperties().forEach((k,v)->result.addProperty(k,v));
      var summary = new PerformanceTestSummary();
      result.setSummaries(List.of(summary));
      summary.setName("Naive performance test");
      summary.setDuration(duration);
      summary.setNumberOfProcessors(Runtime.getRuntime().availableProcessors());
      summary.setMaxMemory(Runtime.getRuntime().maxMemory());

      objectMapper.writeValue(new File("target/performance_test_result.json"), result);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

    private Map<String, Object> getRunProperties() {
        try {
          File runProperties = new File("../run-properties.json");
          if (runProperties.exists()) {
            return objectMapper.readValue(runProperties, HashMap.class);
          } else {
            log.warn("No run properties file found");
            return Map.of();
          }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

  private void createResources(int startIndex, int number, String value) {
    try {
      List<Callable<Void>> callables = new ArrayList<>(number);

      for (int i = startIndex; i < startIndex + number; i++) {
        var res = new SimplePerformanceTestResource();
        res.setMetadata(
            new ObjectMetaBuilder()
                .withAnnotations(Map.of(INDEX, "" + i))
                .withName(RESOURCE_NAME_PREFIX + i)
                .build());
        res.setSpec(new SimplePerformanceTestSpec());
        res.getSpec().setValue(value);
        callables.add(
            () -> {
              extension.create(res);
              return null;
            });
      }
      var futures = executor.invokeAll(callables);
      for (var future : futures) {
        future.get();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static class StatusChecker {
    private final String expectedStatus; // null indicates deleted
    private final Set<Integer> remaining = new ConcurrentHashSet<>();

    StatusChecker(
        String expectedStatus,
        int startIndex,
        int number,
        SharedIndexInformer<SimplePerformanceTestResource> primaryInformer) {
      this.expectedStatus = expectedStatus;
      for (int i = startIndex; i < startIndex + number; i++) {
        remaining.add(i);
      }
      primaryInformer.addEventHandler(
          new ResourceEventHandler<>() {
            @Override
            public void onAdd(SimplePerformanceTestResource obj) {
              checkOnStatus(obj);
            }

            @Override
            public void onUpdate(
                SimplePerformanceTestResource oldObj, SimplePerformanceTestResource newObj) {
              checkOnStatus(newObj);
            }

            @Override
            public void onDelete(
                SimplePerformanceTestResource obj, boolean deletedFinalStateUnknown) {
              if (expectedStatus == null) {
                synchronized (remaining) {
                  remaining.remove(Integer.parseInt(obj.getMetadata().getAnnotations().get(INDEX)));
                  remaining.notifyAll();
                }
              }
            }
          });
      primaryInformer.getStore().list().forEach(this::checkOnStatus);
    }

    private void checkOnStatus(SimplePerformanceTestResource res) {
      if (expectedStatus != null
          && res.getStatus() != null
          && res.getStatus().getValue().equals(expectedStatus)) {
        synchronized (remaining) {
          remaining.remove(Integer.parseInt(res.getMetadata().getAnnotations().get(INDEX)));
          remaining.notifyAll();
        }
      }
    }

    public void waitUntilAllInStatus() {
      synchronized (remaining) {
        while (!remaining.isEmpty()) {
          try {
            remaining.wait();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
          }
        }
      }
    }
  }
}
