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
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetrics;
import io.javaoperatorsdk.operator.sample.dependent.ResourcePollerConfig;
import io.javaoperatorsdk.operator.sample.dependent.SchemaDependentResource;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;

public class MySQLSchemaOperator {

  private static final Logger log = LoggerFactory.getLogger(MySQLSchemaOperator.class);

  public static void main(String[] args) throws IOException {
    log.info("MySQL Schema Operator starting");

    Config config = new ConfigBuilder().withNamespace(null).build();
    KubernetesClient client = new DefaultKubernetesClient(config);
    Operator operator = new Operator(client,
        overrider -> overrider.withMetrics(new MicrometerMetrics(new LoggingMeterRegistry())));

    MySQLSchemaReconciler schemaReconciler = new MySQLSchemaReconciler();

    // override the default configuration
    operator.register(schemaReconciler,
        configOverrider -> configOverrider.replaceNamedDependentResourceConfig(
            SchemaDependentResource.NAME,
            new ResourcePollerConfig(300, MySQLDbConfig.loadFromEnvironmentVars())));
    operator.installShutdownHook();
    operator.start();

    new FtBasic(new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080).start(Exit.NEVER);
  }
}
