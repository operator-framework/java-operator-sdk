package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import static java.lang.String.format;

@Controller(
        crdName = "schemas.mysql.sample.javaoperatorsdk",
        customResourceClass = Schema.class)
public class SchemaController implements ResourceController<Schema> {


    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public Optional<Schema> createOrUpdateResource(Schema schema) {
        try (Connection connection = getConnection()) {
            if (!schemaExists(connection, schema.getMetadata().getName())) {
                connection.createStatement().execute(format("CREATE SCHEMA `%1$s` DEFAULT CHARACTER SET %2$s",
                        schema.getMetadata().getName(),
                        schema.getSpec().getEncoding()));

                SchemaStatus status = new SchemaStatus();
                status.setUrl(format("jdbc:mysql://%1$s/%2$s",
                        System.getenv("MYSQL_HOST"),
                        schema.getMetadata().getName()));
                status.setStatus("CREATED");
                schema.setStatus(status);

                log.info("Schema {} created", schema.getMetadata().getName());
            }
            return Optional.of(schema);
        } catch (SQLException e) {
            log.error("Error while creating Schema", e);

            SchemaStatus status = new SchemaStatus();
            status.setUrl(null);
            status.setStatus("ERROR");
            schema.setStatus(status);

            return Optional.of(schema);
        }
    }

    @Override
    public boolean deleteResource(Schema schema) {
        log.info("Execution deleteResource for: {}", schema.getMetadata().getName());

        try (Connection connection = getConnection()) {
            if (schemaExists(connection, schema.getMetadata().getName())) {
                connection.createStatement().execute("DROP DATABASE `" + schema.getMetadata().getName() + "`");
                log.info("Deleted Schema '{}'", schema.getMetadata().getName());
            } else {
                log.info("Delete event ignored for schema '{}', real schema doesn't exist",
                        schema.getMetadata().getName());
            }
            return true;
        } catch (SQLException e) {
            log.error("Error while trying to delete Schema", e);
            return false;
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(format("jdbc:mysql://%1$s:%2$s?user=%3$s&password=%4$s",
                System.getenv("MYSQL_HOST"),
                System.getenv("MYSQL_PORT") != null ? System.getenv("MYSQL_PORT") : "3306",
                System.getenv("MYSQL_USER"),
                System.getenv("MYSQL_PASSWORD")));
    }

    private boolean schemaExists(Connection connection, String schemaName) throws SQLException {
        ResultSet resultSet = connection.createStatement().executeQuery(
                format("SELECT schema_name FROM information_schema.schemata WHERE schema_name = \"%1$s\"",
                        schemaName));
        return resultSet.first();
    }

}
