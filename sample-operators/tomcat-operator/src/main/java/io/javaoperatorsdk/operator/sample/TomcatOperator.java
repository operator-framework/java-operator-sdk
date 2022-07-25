package io.javaoperatorsdk.operator.sample;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import io.fabric8.kubernetes.client.*;
import io.javaoperatorsdk.operator.Operator;

public class TomcatOperator {

  private static final Logger log = LoggerFactory.getLogger(TomcatOperator.class);

  public static void main(String[] args) throws IOException {

    KubernetesClient client = new KubernetesClientBuilder().build();
    Operator operator = new Operator(client);
    operator.register(new TomcatReconciler());
    operator.register(new WebappReconciler(client));
    operator.installShutdownHook();
    operator.start();

    new FtBasic(new TkFork(new FkRegex("/health", "ALL GOOD.")), 8080).start(Exit.NEVER);
  }
}
