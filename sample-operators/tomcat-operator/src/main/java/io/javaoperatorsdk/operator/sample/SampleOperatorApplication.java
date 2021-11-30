package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.util.List;

import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.sample.tomcat.TomcatReconciler;
import io.javaoperatorsdk.operator.sample.webapp.WebappReconciler;

public class SampleOperatorApplication {

  public static void main(String[] args) throws IOException {
    final var config = new ConfigBuilder().withNamespace(null).build();
    final var client = new DefaultKubernetesClient(config);
    final var operator = new Operator(client, DefaultConfigurationService.instance());

    final List<Reconciler<?>> reconcilerList =
        List.of(new TomcatReconciler(client), new WebappReconciler(client));

    reconcilerList.forEach(operator::register);

    operator.start();

    new FtBasic(new TkFork(new FkRegex("/health", "ALL GOOD.")), 8080).start(Exit.NEVER);
  }

}
