package io.javaoperatorsdk.operator.sample;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Optional;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;

import static java.lang.String.format;

@ControllerConfiguration
public class MySQLSchemaReconciler
    implements Reconciler<MySQLSchema>, ErrorStatusHandler<MySQLSchema> {
  static final String USERNAME_FORMAT = "%s-user";
  static final String SECRET_FORMAT = "%s-secret";

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final KubernetesClient kubernetesClient;
  private final MySQLDbConfig mysqlDbConfig;

  public MySQLSchemaReconciler(KubernetesClient kubernetesClient, MySQLDbConfig mysqlDbConfig) {
    this.kubernetesClient = kubernetesClient;
    this.mysqlDbConfig = mysqlDbConfig;
  }

  @Override
  public UpdateControl<MySQLSchema> reconcile(MySQLSchema schema,
      Context context) {
    try (Connection connection = getConnection()) {
      if (!schemaExists(connection, schema.getMetadata().getName())) {
        try (Statement statement = connection.createStatement()) {
          statement.execute(
              format(
                  "CREATE SCHEMA `%1$s` DEFAULT CHARACTER SET %2$s",
                  schema.getMetadata().getName(), schema.getSpec().getEncoding()));
        }

        String password = RandomStringUtils.randomAlphanumeric(16);
        String userName = String.format(USERNAME_FORMAT, schema.getMetadata().getName());
        String secretName = String.format(SECRET_FORMAT, schema.getMetadata().getName());
        try (Statement statement = connection.createStatement()) {
          statement.execute(format("CREATE USER '%1$s' IDENTIFIED BY '%2$s'", userName, password));
        }
        try (Statement statement = connection.createStatement()) {
          statement.execute(
              format("GRANT ALL ON `%1$s`.* TO '%2$s'", schema.getMetadata().getName(), userName));
        }
        Secret credentialsSecret =
            new SecretBuilder()
                .withNewMetadata()
                .withName(secretName)
                .endMetadata()
                .addToData(
                    "MYSQL_USERNAME", Base64.getEncoder().encodeToString(userName.getBytes()))
                .addToData(
                    "MYSQL_PASSWORD", Base64.getEncoder().encodeToString(password.getBytes()))
                .build();
        this.kubernetesClient
            .secrets()
            .inNamespace(schema.getMetadata().getNamespace())
            .create(credentialsSecret);

        SchemaStatus status = new SchemaStatus();
        status.setUrl(
            format(
                "jdbc:mysql://%1$s/%2$s",
                System.getenv("MYSQL_HOST"), schema.getMetadata().getName()));
        status.setUserName(userName);
        status.setSecretName(secretName);
        status.setStatus("CREATED");
        schema.setStatus(status);
        log.info("Schema {} created - updating CR status", schema.getMetadata().getName());

        return UpdateControl.updateStatus(schema);
      }
      return UpdateControl.noUpdate();
    } catch (SQLException e) {
      log.error("Error while creating Schema", e);
      SchemaStatus status = new SchemaStatus();
      status.setUrl(null);
      status.setUserName(null);
      status.setSecretName(null);
      status.setStatus("ERROR: " + e.getMessage());
      schema.setStatus(status);

      return UpdateControl.updateStatus(schema);
    }
  }

  @Override
  public Optional<MySQLSchema> updateErrorStatus(MySQLSchema resource, RetryInfo retryInfo,
      RuntimeException e) {

    return Optional.empty();
  }

  @Override
  public DeleteControl cleanup(MySQLSchema schema, Context context) {
    log.info("Execution deleteResource for: {}", schema.getMetadata().getName());

    try (Connection connection = getConnection()) {
      if (schemaExists(connection, schema.getMetadata().getName())) {
        try (Statement statement = connection.createStatement()) {
          statement.execute(format("DROP DATABASE `%1$s`", schema.getMetadata().getName()));
        }
        log.info("Deleted Schema '{}'", schema.getMetadata().getName());

        if (schema.getStatus() != null) {
          if (userExists(connection, schema.getStatus().getUserName())) {
            try (Statement statement = connection.createStatement()) {
              statement.execute(format("DROP USER '%1$s'", schema.getStatus().getUserName()));
            }
            log.info("Deleted User '{}'", schema.getStatus().getUserName());
          }
        }

        this.kubernetesClient
            .secrets()
            .inNamespace(schema.getMetadata().getNamespace())
            .withName(schema.getStatus().getSecretName())
            .delete();
      } else {
        log.info(
            "Delete event ignored for schema '{}', real schema doesn't exist",
            schema.getMetadata().getName());
      }
      return DeleteControl.defaultDelete();
    } catch (SQLException e) {
      log.error("Error while trying to delete Schema", e);
      return DeleteControl.noFinalizerRemoval();
    }
  }

  private Connection getConnection() throws SQLException {
    String connectionString =
        format("jdbc:mysql://%1$s:%2$s", mysqlDbConfig.getHost(), mysqlDbConfig.getPort());

    log.debug("Connecting to '{}' with user '{}'", connectionString, mysqlDbConfig.getUser());
    return DriverManager.getConnection(connectionString, mysqlDbConfig.getUser(),
        mysqlDbConfig.getPassword());
  }

  private boolean schemaExists(Connection connection, String schemaName) throws SQLException {
    try (PreparedStatement ps =
        connection.prepareStatement(
            "SELECT * FROM information_schema.schemata WHERE schema_name = ?")) {
      ps.setString(1, schemaName);
      try (ResultSet resultSet = ps.executeQuery()) {
        // CATALOG_NAME, SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME, SQL_PATH
        var exists = resultSet.next();
        return exists;
      }
    }
  }

  private boolean userExists(Connection connection, String userName) throws SQLException {
    try (PreparedStatement ps =
        connection.prepareStatement("SELECT User FROM mysql.user WHERE User = ?")) {
      ps.setString(1, userName);
      try (ResultSet resultSet = ps.executeQuery()) {
        return resultSet.first();
      }
    }
  }
}
