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
package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.sample.probes.LivenessHandler;
import io.javaoperatorsdk.operator.sample.probes.StartupHandler;

import com.sun.net.httpserver.HttpServer;

public class WebPageOperator {
  public static final String WEBPAGE_RECONCILER_ENV = "WEBPAGE_RECONCILER";
  public static final String WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE = "classic";
  public static final String WEBPAGE_MANAGED_DEPENDENT_RESOURCE_ENV_VALUE = "managed";
  private static final Logger log = LoggerFactory.getLogger(WebPageOperator.class);

  /**
   * Based on env variables a different flavor of Reconciler is used, showcasing how the same logic
   * can be implemented using the low level and higher level APIs.
   */
  public static void main(String[] args) throws IOException {
    log.info("WebServer Operator starting!");

    Operator operator = new Operator(o -> o.withStopOnInformerErrorDuringStartup(false));
    String reconcilerEnvVar = System.getenv(WEBPAGE_RECONCILER_ENV);
    if (WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE.equals(reconcilerEnvVar)) {
      operator.register(new WebPageReconciler());
    } else if (WEBPAGE_MANAGED_DEPENDENT_RESOURCE_ENV_VALUE.equals(reconcilerEnvVar)) {
      operator.register(new WebPageManagedDependentsReconciler());
    } else {
      operator.register(new WebPageStandaloneDependentsReconciler());
    }
    operator.start();

    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.createContext("/startup", new StartupHandler(operator));
    // we want to restart the operator if something goes wrong with (maybe just some) event sources
    server.createContext("/healthz", new LivenessHandler(operator));
    server.setExecutor(null);
    server.start();
  }
}
