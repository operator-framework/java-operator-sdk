package io.javaoperatorsdk.operator.sample;

import java.util.Optional;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ContextInitializer;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.sample.schema.Schema;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.NO_FINALIZER;
import static java.lang.String.format;

// todo handle this, should work with finalizer
@ControllerConfiguration(finalizerName = NO_FINALIZER,
    dependents = {
        @Dependent(type = SecretDependentResource.class),
        @Dependent(type = SchemaDependentResource.class)
    })
public class MySQLSchemaReconciler
    implements Reconciler<MySQLSchema>, ErrorStatusHandler<MySQLSchema>,
    ContextInitializer<MySQLSchema> {

  static final String SECRET_FORMAT = "%s-secret";
  static final String USERNAME_FORMAT = "%s-user";

  static final String MYSQL_SECRET_NAME = "mysql.secret.name";
  static final String MYSQL_SECRET_USERNAME = "mysql.secret.user.name";
  static final String MYSQL_SECRET_PASSWORD = "mysql.secret.user.password";
  static final String BUILT_SCHEMA = "built schema";
  static final Logger log = LoggerFactory.getLogger(MySQLSchemaReconciler.class);

  public MySQLSchemaReconciler() {}

  @Override
  public void initContext(MySQLSchema primary, Context context) {
    final var name = primary.getMetadata().getName();
    final var password = RandomStringUtils
        .randomAlphanumeric(16); // NOSONAR: we don't need cryptographically-strong randomness here

    final var secretName = String.format(SECRET_FORMAT, name);
    final var userName = String.format(USERNAME_FORMAT, name);

    // put information in context for other dependents and reconciler to use
    context.put(MYSQL_SECRET_PASSWORD, password);
    context.put(MYSQL_SECRET_NAME, secretName);
    context.put(MYSQL_SECRET_USERNAME, userName);
  }

  @Override
  public UpdateControl<MySQLSchema> reconcile(MySQLSchema schema, Context context) {
    // we only need to update the status if we just built the schema, i.e. when it's present in the
    // context
    return context.get(BUILT_SCHEMA, Schema.class).map(s -> {
      updateStatusPojo(schema, context.getMandatory(MYSQL_SECRET_NAME, String.class),
          context.getMandatory(MYSQL_SECRET_USERNAME, String.class));
      log.info("Schema {} created - updating CR status", schema.getMetadata().getName());
      return UpdateControl.updateStatus(schema);
    }).orElse(UpdateControl.noUpdate());
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
}
