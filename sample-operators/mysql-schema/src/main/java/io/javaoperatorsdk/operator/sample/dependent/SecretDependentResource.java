package io.javaoperatorsdk.operator.sample.dependent;

import java.util.Base64;
import java.util.Set;

import org.apache.commons.lang3.RandomStringUtils;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.sample.MySQLSchema;

@KubernetesDependent
public class SecretDependentResource extends KubernetesDependentResource<Secret, MySQLSchema>
    implements Creator<Secret, MySQLSchema>, SecondaryToPrimaryMapper<Secret> {

  public static final String NAME = "secret";
  public static final String SECRET_SUFFIX = "-secret";
  public static final String SECRET_FORMAT = "%s" + SECRET_SUFFIX;
  public static final String USERNAME_FORMAT = "%s-user";
  public static final String MYSQL_SECRET_USERNAME = "mysql.secret.user.name";
  public static final String MYSQL_SECRET_PASSWORD = "mysql.secret.user.password";

  private static String encode(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes());
  }

  @Override
  protected Secret desired(MySQLSchema schema, Context<MySQLSchema> context) {
    final var password =
        RandomStringUtils.randomAlphanumeric(
            16); // NOSONAR: we don't need cryptographically-strong randomness here
    final var name = schema.getMetadata().getName();
    final var secretName = getSecretName(name);
    final var userName = String.format(USERNAME_FORMAT, name);

    return new SecretBuilder()
        .withNewMetadata()
        .withName(secretName)
        .withNamespace(schema.getMetadata().getNamespace())
        .endMetadata()
        .addToData(MYSQL_SECRET_USERNAME, encode(userName))
        .addToData(MYSQL_SECRET_PASSWORD, encode(password))
        .build();
  }

  private String getSecretName(String schemaName) {
    return String.format(SECRET_FORMAT, schemaName);
  }

  @Override
  public Result<Secret> match(Secret actual, MySQLSchema primary, Context<MySQLSchema> context) {
    final var desiredSecretName = getSecretName(primary.getMetadata().getName());
    return Result.nonComputed(actual.getMetadata().getName().equals(desiredSecretName));
  }

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(Secret resource) {
    String name = resource.getMetadata().getName();
    return Set.of(
        new ResourceID(
            name.substring(0, name.length() - SECRET_SUFFIX.length()),
            resource.getMetadata().getNamespace()));
  }
}
