package io.javaoperatorsdk.operator.sample.schema;

import java.sql.*;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.sample.MySQLDbConfig;

import static java.lang.String.format;

public class SchemaService {

  private static final Logger log = LoggerFactory.getLogger(SchemaService.class);

  private final MySQLDbConfig mySQLDbConfig;

  public SchemaService(MySQLDbConfig mySQLDbConfig) {
    this.mySQLDbConfig = mySQLDbConfig;
  }

  public Optional<Schema> getSchema(String name) {
    try (Connection connection = getConnection()) {
      return getSchema(connection, name);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static Schema createSchemaAndRelatedUser(
      Connection connection, String schemaName, String encoding, String userName, String password) {
    try {
      try (Statement statement = connection.createStatement()) {
        statement.execute(
            format("CREATE SCHEMA `%1$s` DEFAULT CHARACTER SET %2$s", schemaName, encoding));
      }
      if (!userExists(connection, userName)) {
        try (Statement statement = connection.createStatement()) {
          statement.execute(format("CREATE USER '%1$s' IDENTIFIED BY '%2$s'", userName, password));
        }
      }
      try (Statement statement = connection.createStatement()) {
        statement.execute(format("GRANT ALL ON `%1$s`.* TO '%2$s'", schemaName, userName));
      }

      return new Schema(schemaName, encoding);
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void deleteSchemaAndRelatedUser(
      Connection connection, String schemaName, String userName) {
    try {
      if (schemaExists(connection, schemaName)) {
        try (Statement statement = connection.createStatement()) {
          statement.execute(format("DROP DATABASE `%1$s`", schemaName));
        }
        log.info("Deleted Schema '{}'", schemaName);
      }

      if (userName != null && userExists(connection, userName)) {
        try (Statement statement = connection.createStatement()) {
          statement.execute(format("DROP USER '%1$s'", userName));
        }
        log.info("Deleted User '{}'", userName);
      }

    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean userExists(Connection connection, String username) {
    try (PreparedStatement ps =
        connection.prepareStatement("SELECT 1 FROM mysql.user WHERE user = ?")) {
      ps.setString(1, username);
      try (ResultSet resultSet = ps.executeQuery()) {
        return resultSet.next();
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  public static boolean schemaExists(Connection connection, String schemaName) {
    return getSchema(connection, schemaName).isPresent();
  }

  public static Optional<Schema> getSchema(Connection connection, String schemaName) {
    try (PreparedStatement ps =
        connection.prepareStatement(
            "SELECT * FROM information_schema.schemata WHERE schema_name = ?")) {
      ps.setString(1, schemaName);
      try (ResultSet resultSet = ps.executeQuery()) {
        // CATALOG_NAME, SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME, SQL_PATH
        var exists = resultSet.next();
        if (!exists) {
          return Optional.empty();
        } else {
          return Optional.of(
              new Schema(
                  resultSet.getString("SCHEMA_NAME"),
                  resultSet.getString("DEFAULT_CHARACTER_SET_NAME")));
        }
      }
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }

  private Connection getConnection() {
    try {
      String connectionString =
          format("jdbc:mysql://%1$s:%2$s", mySQLDbConfig.getHost(), mySQLDbConfig.getPort());

      log.debug("Connecting to '{}' with user '{}'", connectionString, mySQLDbConfig.getUser());
      return DriverManager.getConnection(
          connectionString, mySQLDbConfig.getUser(), mySQLDbConfig.getPassword());
    } catch (SQLException e) {
      throw new IllegalStateException(e);
    }
  }
}
