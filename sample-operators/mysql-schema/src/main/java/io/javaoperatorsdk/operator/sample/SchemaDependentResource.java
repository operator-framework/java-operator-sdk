package io.javaoperatorsdk.operator.sample;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.sample.schema.Schema;
import io.javaoperatorsdk.operator.sample.schema.SchemaService;

import static java.lang.String.format;

public class SchemaDependentResource extends
    AbstractDependentResource<Schema, MySQLSchema, ResourcePollerConfig> {

  private MySQLDbConfig dbConfig;
  private int pollPeriod;

  public SchemaDependentResource() {}

  @Override
  public void configWith(ResourcePollerConfig config) {
    this.dbConfig = config.getMySQLDbConfig();
    this.pollPeriod = config.getPollPeriod();
  }

  @Override
  public Optional<EventSource> eventSource(EventSourceContext<MySQLSchema> context) {
    return Optional.of(new PerResourcePollingEventSource<>(
        new SchemaPollingResourceSupplier(dbConfig), context.getPrimaryCache(), pollPeriod,
        Schema.class));
  }

  @Override
  public Schema desired(MySQLSchema primary, Context context) {
    return new Schema(primary.getMetadata().getName(), primary.getSpec().getEncoding());
  }

  @Override
  protected boolean match(Schema actual, Schema target, Context context) {
    return actual.equals(target);
  }

  @Override
  protected Schema create(Schema target, MySQLSchema mySQLSchema, Context context) {
    try (Connection connection = getConnection()) {
      final var schema = SchemaService.createSchemaAndRelatedUser(
          connection,
          target.getName(),
          target.getCharacterSet(),
          context.getMandatory(MySQLSchemaReconciler.MYSQL_SECRET_USERNAME, String.class),
          context.getMandatory(MySQLSchemaReconciler.MYSQL_SECRET_PASSWORD, String.class));

      // put the newly built schema in the context to let the reconciler know we just built it
      context.put(MySQLSchemaReconciler.BUILT_SCHEMA, schema);
      return schema;
    } catch (SQLException e) {
      MySQLSchemaReconciler.log.error("Error while creating Schema", e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  protected Schema update(Schema actual, Schema target, MySQLSchema mySQLSchema, Context context) {
    throw new IllegalStateException("Target schema should not be changed: " + mySQLSchema);
  }

  private Connection getConnection() throws SQLException {
    String connectURL = format("jdbc:mysql://%1$s:%2$s", dbConfig.getHost(), dbConfig.getPort());

    MySQLSchemaReconciler.log.debug("Connecting to '{}' with user '{}'", connectURL,
        dbConfig.getUser());
    return DriverManager.getConnection(connectURL, dbConfig.getUser(), dbConfig.getPassword());
  }

  @Override
  public void delete(MySQLSchema primary, Context context) {
    try (Connection connection = getConnection()) {
      var userName = primary.getStatus() != null ? primary.getStatus().getUserName() : null;
      SchemaService.deleteSchemaAndRelatedUser(connection, primary.getMetadata().getName(),
          userName);
    } catch (SQLException e) {
      throw new RuntimeException("Error while trying to delete Schema", e);
    }
  }

  // todo this should read the resource from event source?
  @Override
  public Optional<Schema> getResource(MySQLSchema primaryResource) {
    try (Connection connection = getConnection()) {
      var schema =
          SchemaService.getSchema(connection, primaryResource.getMetadata().getName()).orElse(null);
      return Optional.ofNullable(schema);
    } catch (SQLException e) {
      throw new RuntimeException("Error while trying read Schema", e);
    }
  }

}
