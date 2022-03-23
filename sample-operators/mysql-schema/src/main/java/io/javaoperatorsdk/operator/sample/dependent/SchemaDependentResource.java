package io.javaoperatorsdk.operator.sample.dependent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource;
import io.javaoperatorsdk.operator.sample.MySQLDbConfig;
import io.javaoperatorsdk.operator.sample.MySQLSchema;
import io.javaoperatorsdk.operator.sample.schema.Schema;
import io.javaoperatorsdk.operator.sample.schema.SchemaService;

import static io.javaoperatorsdk.operator.sample.dependent.SecretDependentResource.MYSQL_SECRET_PASSWORD;
import static io.javaoperatorsdk.operator.sample.dependent.SecretDependentResource.MYSQL_SECRET_USERNAME;
import static java.lang.String.format;

public class SchemaDependentResource
    extends PerResourcePollingDependentResource<Schema, MySQLSchema>
    implements EventSourceProvider<MySQLSchema>,
    DependentResourceConfigurator<ResourcePollerConfig>,
    Creator<Schema, MySQLSchema>, Deleter<MySQLSchema> {
  public static final String NAME = "schema";
  private static final Logger log = LoggerFactory.getLogger(SchemaDependentResource.class);

  private MySQLDbConfig dbConfig;

  public SchemaDependentResource() {
    super(Schema.class);
  }

  @Override
  public void configureWith(ResourcePollerConfig config) {
    this.dbConfig = config.getMySQLDbConfig();
    setPollingPeriod(config.getPollPeriod());
  }

  @Override
  public Schema desired(MySQLSchema primary, Context<MySQLSchema> context) {
    return new Schema(primary.getMetadata().getName(), primary.getSpec().getEncoding());
  }

  @Override
  public Schema create(Schema target, MySQLSchema mySQLSchema, Context<MySQLSchema> context) {
    try (Connection connection = getConnection()) {
      Secret secret = context.getSecondaryResource(Secret.class).orElseThrow();
      var username = decode(secret.getData().get(MYSQL_SECRET_USERNAME));
      var password = decode(secret.getData().get(MYSQL_SECRET_PASSWORD));
      return SchemaService.createSchemaAndRelatedUser(
          connection,
          target.getName(),
          target.getCharacterSet(), username, password);
    } catch (SQLException e) {
      log.error("Error while creating Schema", e);
      throw new IllegalStateException(e);
    }
  }

  private Connection getConnection() throws SQLException {
    String connectURL = format("jdbc:mysql://%1$s:%2$s", dbConfig.getHost(), dbConfig.getPort());
    log.debug("Connecting to '{}' with user '{}'", connectURL,
        dbConfig.getUser());
    return DriverManager.getConnection(connectURL, dbConfig.getUser(), dbConfig.getPassword());
  }

  @Override
  public void delete(MySQLSchema primary, Context<MySQLSchema> context) {
    try (Connection connection = getConnection()) {
      var userName = primary.getStatus() != null ? primary.getStatus().getUserName() : null;
      SchemaService.deleteSchemaAndRelatedUser(connection, primary.getMetadata().getName(),
          userName);
    } catch (SQLException e) {
      throw new RuntimeException("Error while trying to delete Schema", e);
    }
  }

  @Override
  public Optional<Schema> fetchResource(MySQLSchema primaryResource) {
    try (Connection connection = getConnection()) {
      var schema =
          SchemaService.getSchema(connection, primaryResource.getMetadata().getName()).orElse(null);
      return Optional.ofNullable(schema);
    } catch (SQLException e) {
      throw new RuntimeException("Error while trying read Schema", e);
    }
  }

  public static String decode(String value) {
    return new String(Base64.getDecoder().decode(value.getBytes()));
  }

}
