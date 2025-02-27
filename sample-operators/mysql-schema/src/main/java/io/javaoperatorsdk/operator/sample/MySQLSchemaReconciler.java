package io.javaoperatorsdk.operator.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.sample.dependent.SchemaDependentResource;
import io.javaoperatorsdk.operator.sample.dependent.SecretDependentResource;
import io.javaoperatorsdk.operator.sample.schema.Schema;

import static io.javaoperatorsdk.operator.sample.dependent.SchemaDependentResource.decode;
import static io.javaoperatorsdk.operator.sample.dependent.SecretDependentResource.MYSQL_SECRET_USERNAME;
import static java.lang.String.format;

@Workflow(
    dependents = {
      @Dependent(type = SecretDependentResource.class, name = SecretDependentResource.NAME),
      @Dependent(
          type = SchemaDependentResource.class,
          name = SchemaDependentResource.NAME,
          dependsOn = SecretDependentResource.NAME)
    })
@ControllerConfiguration
public class MySQLSchemaReconciler implements Reconciler<MySQLSchema> {

  static final Logger log = LoggerFactory.getLogger(MySQLSchemaReconciler.class);

  @Override
  public UpdateControl<MySQLSchema> reconcile(MySQLSchema schema, Context<MySQLSchema> context) {
    // we only need to update the status if we just built the schema, i.e. when it's present in the
    // context
    Secret secret = context.getSecondaryResource(Secret.class).orElseThrow();

    return context
        .getSecondaryResource(Schema.class, SchemaDependentResource.NAME)
        .map(
            s -> {
              var statusUpdateResource =
                  createForStatusUpdate(
                      schema,
                      s,
                      secret.getMetadata().getName(),
                      decode(secret.getData().get(MYSQL_SECRET_USERNAME)));
              log.info("Schema {} created - updating CR status", s.getName());
              return UpdateControl.patchStatus(statusUpdateResource);
            })
        .orElseGet(UpdateControl::noUpdate);
  }

  @Override
  public ErrorStatusUpdateControl<MySQLSchema> updateErrorStatus(
      MySQLSchema schema, Context<MySQLSchema> context, Exception e) {
    SchemaStatus status = new SchemaStatus();
    status.setUrl(null);
    status.setUserName(null);
    status.setSecretName(null);
    status.setStatus("ERROR: " + e.getMessage());
    schema.setStatus(status);
    return ErrorStatusUpdateControl.patchStatus(schema);
  }

  private MySQLSchema createForStatusUpdate(
      MySQLSchema mySQLSchema, Schema schema, String secretName, String userName) {
    MySQLSchema res = new MySQLSchema();
    res.setMetadata(
        new ObjectMetaBuilder()
            .withName(mySQLSchema.getMetadata().getName())
            .withNamespace(mySQLSchema.getMetadata().getNamespace())
            .build());
    SchemaStatus status = new SchemaStatus();
    status.setUrl(format("jdbc:mysql://%1$s/%2$s", System.getenv("MYSQL_HOST"), schema.getName()));
    status.setUserName(userName);
    status.setSecretName(secretName);
    status.setStatus("CREATED");
    res.setStatus(status);
    return res;
  }
}
