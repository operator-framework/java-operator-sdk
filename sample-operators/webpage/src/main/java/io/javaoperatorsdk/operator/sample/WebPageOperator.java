package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.*;
import io.javaoperatorsdk.operator.Operator;

import com.sun.net.httpserver.HttpServer;

public class WebPageOperator {
  public static final String WEBPAGE_RECONCILER_ENV = "WEBPAGE_RECONCILER";
  public static final String WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE = "classic";
  public static final String WEBPAGE_MANAGED_DEPENDENT_RESOURCE_ENV_VALUE = "managed";
  private static final Logger log = LoggerFactory.getLogger(WebPageOperator.class);


  public static void main(String[] args) throws IOException {
    log.info("WebServer Operator starting!");

    KubernetesClient client = new KubernetesClientBuilder().build();
    Operator operator = new Operator(client, o -> o.withStopOnInformerErrorDuringStartup(false));
    String reconcilerEnvVar = System.getenv(WEBPAGE_RECONCILER_ENV);
    if (WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE.equals(reconcilerEnvVar)) {
      operator.register(new WebPageReconciler(client));
    } else if (WEBPAGE_MANAGED_DEPENDENT_RESOURCE_ENV_VALUE
        .equals(reconcilerEnvVar)) {
      operator.register(new WebPageManagedDependentsReconciler());
    } else {
      operator.register(new WebPageStandaloneDependentsReconciler(client));
    }
    operator.installShutdownHook();
    operator.start();

    HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.createContext("/startup", new StartupHandler(operator));
    // we want to restart the operator if something goes wrong with (maybe just some) event sources
    server.createContext("/healthz", new LivenessHandler(operator));
    server.setExecutor(null);
    server.start();
  }
}
