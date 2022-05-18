package io.javaoperatorsdk.operator.sample;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;

public class WebPageOperator {
  public static final String WEBPAGE_RECONCILER_ENV = "WEBPAGE_RECONCILER";
  public static final String WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE = "classic";
  public static final String WEBPAGE_MANAGED_DEPENDENT_RESOURCE_ENV_VALUE = "managed";
  private static final Logger log = LoggerFactory.getLogger(WebPageOperator.class);


  public static void main(String[] args) throws IOException {
    log.info("WebServer Operator starting!");

    Config config = new ConfigBuilder().withNamespace(null).build();
    KubernetesClient client = new DefaultKubernetesClient(config);
    Operator operator = new Operator(client);
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

    new FtBasic(new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080).start(Exit.NEVER);
  }
}
