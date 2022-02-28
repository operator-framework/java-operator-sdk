package io.javaoperatorsdk.operator.sample.dependent;

import java.util.Optional;

import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.sample.MySQLDbConfig;
import io.javaoperatorsdk.operator.sample.MySQLSchema;
import io.javaoperatorsdk.operator.sample.schema.Schema;
import io.javaoperatorsdk.operator.sample.schema.SchemaService;

public class SchemaPollingResourceSupplier
    implements PerResourcePollingEventSource.ResourceSupplier<Schema, MySQLSchema> {

  private final SchemaService schemaService;

  public SchemaPollingResourceSupplier(MySQLDbConfig mySQLDbConfig) {
    this.schemaService = new SchemaService(mySQLDbConfig);
  }

  @Override
  public Optional<Schema> getResource(MySQLSchema resource) {
    return schemaService.getSchema(resource.getMetadata().getName());
  }
}
