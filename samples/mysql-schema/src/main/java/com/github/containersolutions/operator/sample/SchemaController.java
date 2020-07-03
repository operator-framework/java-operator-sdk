package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Optional;

import static java.lang.String.format;

@Controller(
        crdName = "schemas.mysql.sample.javaoperatorsdk",
        customResourceClass = Schema.class)
public class SchemaController implements ResourceController<Schema> {
    static final String USERNAME_FORMAT = "%s-user";
    static final String SECRET_FORMAT = "%s-secret";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final KubernetesClient kubernetesClient;

    public SchemaController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public Optional<Schema> createOrUpdateResource(Schema schema, Context<Schema> context) {
        try (Connection connection = getConnection()) {
            if (!schemaExists(connection, schema.getMetadata().getName())) {
                connection.createStatement().execute(format("CREATE SCHEMA `%1$s` DEFAULT CHARACTER SET %2$s",
                        schema.getMetadata().getName(),
                        schema.getSpec().getEncoding()));

                String password = RandomStringUtils.randomAlphanumeric(16);
                String userName = String.format(USERNAME_FORMAT,
                        schema.getMetadata().getName());
                String secretName = String.format(SECRET_FORMAT,
                        schema.getMetadata().getName());
                connection.createStatement().execute(format(
                        "CREATE USER '%1$s' IDENTIFIED BY '%2$s'",
                        userName, password));
                connection.createStatement().execute(format(
                        "GRANT ALL ON `%1$s`.* TO '%2$s'",
                        schema.getMetadata().getName(), userName));
                Secret credentialsSecret = new SecretBuilder()
                        .withNewMetadata().withName(secretName).endMetadata()
                        .addToData("MYSQL_USERNAME", Base64.getEncoder().encodeToString(userName.getBytes()))
                        .addToData("MYSQL_PASSWORD", Base64.getEncoder().encodeToString(password.getBytes()))
                        .build();
                this.kubernetesClient.secrets()
                        .inNamespace(schema.getMetadata().getNamespace())
                        .create(credentialsSecret);

                SchemaStatus status = new SchemaStatus();
                status.setUrl(format("jdbc:mysql://%1$s/%2$s",
                        System.getenv("MYSQL_HOST"),
                        schema.getMetadata().getName()));
                status.setUserName(userName);
                status.setSecretName(secretName);
                status.setStatus("CREATED");
                schema.setStatus(status);
                log.info("Schema {} created", schema.getMetadata().getName());

                return Optional.of(schema);
            }
            return Optional.empty();
        } catch (SQLException e) {
            log.error("Error while creating Schema", e);

            SchemaStatus status = new SchemaStatus();
            status.setUrl(null);
            status.setUserName(null);
            status.setSecretName(null);
            status.setStatus("ERROR");
            schema.setStatus(status);

            return Optional.of(schema);
        }
    }

    @Override
    public boolean deleteResource(Schema schema, Context<Schema> context) {
        log.info("Execution deleteResource for: {}", schema.getMetadata().getName());

        try (Connection connection = getConnection()) {
            if (schemaExists(connection, schema.getMetadata().getName())) {
                connection.createStatement().execute("DROP DATABASE `" + schema.getMetadata().getName() + "`");
                log.info("Deleted Schema '{}'", schema.getMetadata().getName());

                if (userExists(connection, schema.getStatus().getUserName())) {
                    connection.createStatement().execute("DROP USER '" + schema.getStatus().getUserName() + "'");
                    log.info("Deleted User '{}'", schema.getStatus().getUserName());
                }

                this.kubernetesClient.secrets()
                        .inNamespace(schema.getMetadata().getNamespace())
                        .withName(schema.getStatus().getSecretName())
                        .delete();
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

    private boolean userExists(Connection connection, String userName) throws SQLException {
        ResultSet resultSet = connection.createStatement().executeQuery(
                format("SELECT User FROM mysql.user WHERE User='%1$s'", userName)
        );
        return resultSet.first();
    }
}
