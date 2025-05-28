package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetrics;
import io.javaoperatorsdk.operator.sample.dependent.ResourcePollerConfig;
import io.javaoperatorsdk.operator.sample.dependent.SchemaDependentResource;
import io.micrometer.core.instrument.logging.LoggingMeterRegistry;

public class MySQLSchemaOperator {

  private static final Logger log = LoggerFactory.getLogger(MySQLSchemaOperator.class);

  public static void main(String[] args) throws IOException {
    log.info("MySQL Schema Operator starting");

    Operator operator =
        new Operator(
            overrider ->
                overrider.withMetrics(
                    MicrometerMetrics.withoutPerResourceMetrics(new LoggingMeterRegistry())));

    MySQLSchemaReconciler schemaReconciler = new MySQLSchemaReconciler();

    // override the default configuration
    operator.register(
        schemaReconciler,
        configOverrider ->
            configOverrider.replacingNamedDependentResourceConfig(
                SchemaDependentResource.NAME,
                new ResourcePollerConfig(
                    Duration.ofMillis(300), MySQLDbConfig.loadFromEnvironmentVars())));
    operator.start();

    new FtBasic(new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080).start(Exit.NEVER);
  }
}
