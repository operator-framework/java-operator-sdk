package io.javaoperatorsdk.operator.sample;

import java.util.Base64;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;

import static io.javaoperatorsdk.operator.sample.MySQLSchemaReconciler.*;

public class SecretDependentResource extends KubernetesDependentResource<Secret, MySQLSchema>
    implements AssociatedSecondaryResourceIdentifier<MySQLSchema>, Matcher<Secret> {

  private static String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes());
  }

  @Override
  protected Secret desired(MySQLSchema schema, Context context) {
    return new SecretBuilder()
        .withNewMetadata()
        .withName(context.getMandatory(MYSQL_SECRET_NAME, String.class))
        .withNamespace(schema.getMetadata().getNamespace())
        .endMetadata()
        .addToData(
            "MYSQL_USERNAME", encode(context.getMandatory(MYSQL_SECRET_USERNAME, String.class)))
        .addToData(
            "MYSQL_PASSWORD", encode(context.getMandatory(MYSQL_SECRET_PASSWORD, String.class)))
        .build();
  }

  @Override
  public boolean match(Secret actual, Secret target, Context context) {
    return ResourceID.fromResource(actual).equals(ResourceID.fromResource(target));
  }

  @Override
  public ResourceID associatedSecondaryID(MySQLSchema primary) {
    return new ResourceID(
        String.format(SECRET_FORMAT, primary.getMetadata().getName()),
        primary.getMetadata().getNamespace());
  }
}
