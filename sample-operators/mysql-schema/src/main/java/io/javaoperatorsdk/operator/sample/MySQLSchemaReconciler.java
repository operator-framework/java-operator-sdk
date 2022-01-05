package io.javaoperatorsdk.operator.sample;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.sample.schema.Schema;
import io.javaoperatorsdk.operator.sample.schema.SchemaService;

import static java.lang.String.format;

@ControllerConfiguration
public class MySQLSchemaReconciler
    implements Reconciler<MySQLSchema>, ErrorStatusHandler<MySQLSchema>,
    EventSourceInitializer<MySQLSchema> {
  public static final String SECRET_FORMAT = "%s-secret";
  public static final String USERNAME_FORMAT = "%s-user";
  public static final int POLL_PERIOD = 500;
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final KubernetesClient kubernetesClient;
  private final MySQLDbConfig mysqlDbConfig;

  public MySQLSchemaReconciler(KubernetesClient kubernetesClient, MySQLDbConfig mysqlDbConfig) {
    this.kubernetesClient = kubernetesClient;
    this.mysqlDbConfig = mysqlDbConfig;
  }

  @Override
  public List<EventSource> prepareEventSources(
      EventSourceInitializationContext<MySQLSchema> context) {
    return List.of(new PerResourcePollingEventSource<>(
        new SchemaPollingResourceSupplier(mysqlDbConfig), context.getPrimaryCache(), POLL_PERIOD,
        Schema.class));
  }

  @Override
  public UpdateControl<MySQLSchema> reconcile(MySQLSchema schema, Context context) {
    log.info("Reconciling MySQLSchema with name: {}", schema.getMetadata().getName());
    var dbSchema = context.getSecondaryResource(Schema.class);
    log.debug("Schema: {} found for: {} ", dbSchema, schema.getMetadata().getName());
    try (Connection connection = getConnection()) {
      if (dbSchema.isEmpty()) {
        log.debug("Creating Schema and related resources for: {}", schema.getMetadata().getName());
        var schemaName = schema.getMetadata().getName();
        String password = RandomStringUtils.randomAlphanumeric(16);
        String secretName = String.format(SECRET_FORMAT, schemaName);
        String userName = String.format(USERNAME_FORMAT, schemaName);

        SchemaService.createSchemaAndRelatedUser(connection, schemaName,
            schema.getSpec().getEncoding(), userName, password);
        createSecret(schema, password, secretName, userName);
        updateStatusPojo(schema, secretName, userName);
        log.info("Schema {} created - updating CR status", schema.getMetadata().getName());
        return UpdateControl.updateStatus(schema);
      } else {
        log.debug("No update on MySQLSchema with name: {}", schema.getMetadata().getName());
        return UpdateControl.noUpdate();
      }
    } catch (SQLException e) {
      log.error("Error while creating Schema", e);
      throw new IllegalStateException(e);
    }
  }

  @Override
  public DeleteControl cleanup(MySQLSchema schema, Context context) {
    log.info("Cleaning up for: {}", schema.getMetadata().getName());
    try (Connection connection = getConnection()) {
      var dbSchema = SchemaService.getSchema(connection, schema.getMetadata().getName());
      if (dbSchema.isPresent()) {
        var userName = schema.getStatus() != null ? schema.getStatus().getUserName() : null;
        SchemaService.deleteSchemaAndRelatedUser(connection, schema.getMetadata().getName(),
            userName);
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

  @Override
  public Optional<MySQLSchema> updateErrorStatus(MySQLSchema schema, RetryInfo retryInfo,
      RuntimeException e) {
    SchemaStatus status = new SchemaStatus();
    status.setUrl(null);
    status.setUserName(null);
    status.setSecretName(null);
    status.setStatus("ERROR: " + e.getMessage());
    schema.setStatus(status);
    return Optional.empty();
  }

  private Connection getConnection() throws SQLException {
    String connectionString =
        format("jdbc:mysql://%1$s:%2$s", mysqlDbConfig.getHost(), mysqlDbConfig.getPort());

    log.debug("Connecting to '{}' with user '{}'", connectionString, mysqlDbConfig.getUser());
    return DriverManager.getConnection(connectionString, mysqlDbConfig.getUser(),
        mysqlDbConfig.getPassword());
  }

  private void updateStatusPojo(MySQLSchema schema, String secretName, String userName) {
    SchemaStatus status = new SchemaStatus();
    status.setUrl(
        format(
            "jdbc:mysql://%1$s/%2$s",
            System.getenv("MYSQL_HOST"), schema.getMetadata().getName()));
    status.setUserName(userName);
    status.setSecretName(secretName);
    status.setStatus("CREATED");
    schema.setStatus(status);
  }

  private void createSecret(MySQLSchema schema, String password, String secretName,
      String userName) {

    var currentSecret = kubernetesClient.secrets().inNamespace(schema.getMetadata().getNamespace())
        .withName(secretName).get();
    if (currentSecret != null) {
      return;
    }
    Secret credentialsSecret =
        new SecretBuilder()
            .withNewMetadata()
            .withName(secretName)
            .withOwnerReferences(new OwnerReference("mysql.sample.javaoperatorsdk/v1",
                false, false, "MySQLSchema",
                schema.getMetadata().getName(), schema.getMetadata().getUid()))
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
  }


}
