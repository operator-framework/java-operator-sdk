package io.javaoperatorsdk.operator.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusHandler;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.sample.dependent.SchemaDependentResource;
import io.javaoperatorsdk.operator.sample.dependent.SecretDependentResource;

import static io.javaoperatorsdk.operator.sample.dependent.SchemaDependentResource.decode;
import static io.javaoperatorsdk.operator.sample.dependent.SecretDependentResource.MYSQL_SECRET_USERNAME;
import static java.lang.String.format;

@ControllerConfiguration(
    dependents = {
        @Dependent(type = SecretDependentResource.class),
        @Dependent(type = SchemaDependentResource.class, name = SchemaDependentResource.NAME)
    })
public class MySQLSchemaReconciler
    implements Reconciler<MySQLSchema>, ErrorStatusHandler<MySQLSchema> {

  static final Logger log = LoggerFactory.getLogger(MySQLSchemaReconciler.class);

  public MySQLSchemaReconciler() {}


  @Override
  public UpdateControl<MySQLSchema> reconcile(MySQLSchema schema, Context<MySQLSchema> context) {
    // we only need to update the status if we just built the schema, i.e. when it's present in the
    // context
    Secret secret = context.getSecondaryResource(Secret.class).orElseThrow();
    return context.getSecondaryResource(MySQLSchema.class).map(s -> {
      updateStatusPojo(schema, secret.getMetadata().getName(),
          decode(secret.getData().get(MYSQL_SECRET_USERNAME)));
      log.info("Schema {} created - updating CR status", schema.getMetadata().getName());
      return UpdateControl.updateStatus(schema);
    }).orElse(UpdateControl.noUpdate());
  }

  @Override
  public ErrorStatusUpdateControl<MySQLSchema> updateErrorStatus(MySQLSchema schema,
      Context<MySQLSchema> context,
      Exception e) {
    SchemaStatus status = new SchemaStatus();
    status.setUrl(null);
    status.setUserName(null);
    status.setSecretName(null);
    status.setStatus("ERROR: " + e.getMessage());
    schema.setStatus(status);
    return ErrorStatusUpdateControl.updateStatus(schema);
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
