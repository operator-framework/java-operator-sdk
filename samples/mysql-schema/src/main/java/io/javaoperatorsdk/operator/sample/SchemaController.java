package io.javaoperatorsdk.operator.sample;

import static java.lang.String.format;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class SchemaController implements ResourceController<Schema> {
  static final String USERNAME_FORMAT = "%s-user";
  static final String SECRET_FORMAT = "%s-secret";

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final KubernetesClient kubernetesClient;

  public SchemaController(KubernetesClient kubernetesClient) {
    this.kubernetesClient = kubernetesClient;
  }

  @Override
  public UpdateControl<Schema> createOrUpdateResource(Schema schema, Context<Schema> context) {
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

        return UpdateControl.updateStatusSubResource(schema);
      }
      return UpdateControl.noUpdate();
    } catch (SQLException e) {
      log.error("Error while creating Schema", e);

      SchemaStatus status = new SchemaStatus();
      status.setUrl(null);
      status.setUserName(null);
      status.setSecretName(null);
      status.setStatus("ERROR");
      schema.setStatus(status);

      return UpdateControl.updateCustomResource(schema);
    }
  }

  @Override
  public DeleteControl deleteResource(Schema schema, Context<Schema> context) {
    log.info("Execution deleteResource for: {}", schema.getMetadata().getName());

    try (Connection connection = getConnection()) {
      if (schemaExists(connection, schema.getMetadata().getName())) {
        try (Statement statement = connection.createStatement()) {
          statement.execute(format("DROP DATABASE `%1$s`", schema.getMetadata().getName()));
        }
        log.info("Deleted Schema '{}'", schema.getMetadata().getName());

        if (userExists(connection, schema.getStatus().getUserName())) {
          try (Statement statement = connection.createStatement()) {
            statement.execute(format("DROP USER '%1$s'", schema.getStatus().getUserName()));
          }
          log.info("Deleted User '{}'", schema.getStatus().getUserName());
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
      return DeleteControl.DEFAULT_DELETE;
    } catch (SQLException e) {
      log.error("Error while trying to delete Schema", e);
      return DeleteControl.NO_FINALIZER_REMOVAL;
    }
  }

  private Connection getConnection() throws SQLException {
    return DriverManager.getConnection(
        format(
            "jdbc:mysql://%1$s:%2$s?user=%3$s&password=%4$s",
            System.getenv("MYSQL_HOST"),
            System.getenv("MYSQL_PORT") != null ? System.getenv("MYSQL_PORT") : "3306",
            System.getenv("MYSQL_USER"),
            System.getenv("MYSQL_PASSWORD")));
  }

  private boolean schemaExists(Connection connection, String schemaName) throws SQLException {
    try (PreparedStatement ps =
        connection.prepareStatement(
            "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?")) {
      ps.setString(1, schemaName);
      try (ResultSet resultSet = ps.executeQuery()) {
        return resultSet.first();
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
