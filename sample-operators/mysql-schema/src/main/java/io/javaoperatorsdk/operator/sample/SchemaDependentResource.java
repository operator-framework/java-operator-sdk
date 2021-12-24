package io.javaoperatorsdk.operator.sample;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Builder;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Persister;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.sample.schema.Schema;
import io.javaoperatorsdk.operator.sample.schema.SchemaService;

import static java.lang.String.format;

public class SchemaDependentResource
    implements DependentResource<Schema, MySQLSchema>, Builder<Schema, MySQLSchema>,
    Cleaner<Schema, MySQLSchema>, Persister<Schema, MySQLSchema> {

  private static final int POLL_PERIOD = 500;
  private MySQLDbConfig dbConfig;

  @Override
  public EventSource initEventSource(EventSourceContext<MySQLSchema> context) {
    dbConfig = context.getMandatory(MySQLSchemaReconciler.MYSQL_DB_CONFIG, MySQLDbConfig.class);
    return new PerResourcePollingEventSource<>(
        new SchemaPollingResourceSupplier(dbConfig), context.getPrimaryCache(), POLL_PERIOD,
        Schema.class);
  }

  @Override
  public Schema buildFor(MySQLSchema primary, Context context) {
    try (Connection connection = getConnection()) {
      final var schema = SchemaService.createSchemaAndRelatedUser(
          connection,
          primary.getMetadata().getName(),
          primary.getSpec().getEncoding(),
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

  private Connection getConnection() throws SQLException {
    String connectURL = format("jdbc:mysql://%1$s:%2$s", dbConfig.getHost(), dbConfig.getPort());

    MySQLSchemaReconciler.log.debug("Connecting to '{}' with user '{}'", connectURL,
        dbConfig.getUser());
    return DriverManager.getConnection(connectURL, dbConfig.getUser(), dbConfig.getPassword());
  }

  @Override
  public void delete(Schema fetched, MySQLSchema primary, Context context) {
    try (Connection connection = getConnection()) {
      var userName = primary.getStatus() != null ? primary.getStatus().getUserName() : null;
      SchemaService.deleteSchemaAndRelatedUser(connection, primary.getMetadata().getName(),
          userName);
    } catch (SQLException e) {
      throw new RuntimeException("Error while trying to delete Schema", e);
    }
  }

  @Override
  public void createOrReplace(Schema dependentResource, Context context) {
    // this is actually implemented in buildFor, the cleaner way to do this would be to have all
    // the needed information in Schema instead of creating both the schema and user from
    // heterogeneous information
  }

  @Override
  public Schema getFor(MySQLSchema primary, Context context) {
    try (Connection connection = getConnection()) {
      return SchemaService.getSchema(connection, primary.getMetadata().getName()).orElse(null);
    } catch (SQLException e) {
      throw new RuntimeException("Error while trying to delete Schema", e);
    }
  }
}
