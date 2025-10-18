/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.sample.dependent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.ConfigurationConverter;
import io.javaoperatorsdk.operator.api.config.dependent.Configured;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ConfiguredDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource;
import io.javaoperatorsdk.operator.sample.MySQLDbConfig;
import io.javaoperatorsdk.operator.sample.MySQLSchema;
import io.javaoperatorsdk.operator.sample.dependent.SchemaDependentResource.ResourcePollerConfigConverter;
import io.javaoperatorsdk.operator.sample.schema.Schema;
import io.javaoperatorsdk.operator.sample.schema.SchemaService;

import static io.javaoperatorsdk.operator.sample.dependent.SecretDependentResource.MYSQL_SECRET_PASSWORD;
import static io.javaoperatorsdk.operator.sample.dependent.SecretDependentResource.MYSQL_SECRET_USERNAME;
import static java.lang.String.format;

@SchemaConfig(
    pollPeriod = 400,
    host = "127.0.0.1",
    port = SchemaDependentResource.LOCAL_PORT,
    user = "root",
    password = "password") // NOSONAR: password is only used locally, example only
@Configured(
    by = SchemaConfig.class,
    with = ResourcePollerConfig.class,
    converter = ResourcePollerConfigConverter.class)
public class SchemaDependentResource
    extends PerResourcePollingDependentResource<Schema, MySQLSchema, String>
    implements ConfiguredDependentResource<ResourcePollerConfig>,
        Creator<Schema, MySQLSchema>,
        Deleter<MySQLSchema> {

  public static final String NAME = "schema";
  public static final int LOCAL_PORT = 3307;
  private static final Logger log = LoggerFactory.getLogger(SchemaDependentResource.class);

  private MySQLDbConfig dbConfig;

  @Override
  public Optional<ResourcePollerConfig> configuration() {
    return Optional.of(new ResourcePollerConfig(getPollingPeriod(), dbConfig));
  }

  @Override
  public void configureWith(ResourcePollerConfig config) {
    this.dbConfig = config.getMySQLDbConfig();
    setPollingPeriod(config.getPollPeriod());
  }

  @Override
  public Schema desired(MySQLSchema primary, Context<MySQLSchema> context) {
    var desired = new Schema(primary.getMetadata().getName(), primary.getSpec().getEncoding());
    log.debug("Desired schema: {}", desired);
    return desired;
  }

  @Override
  public Schema create(Schema target, MySQLSchema mySQLSchema, Context<MySQLSchema> context) {
    try (Connection connection = getConnection()) {
      Secret secret = context.getSecondaryResource(Secret.class).orElseThrow();
      var username = decode(secret.getData().get(MYSQL_SECRET_USERNAME));
      var password = decode(secret.getData().get(MYSQL_SECRET_PASSWORD));
      log.debug("Creating schema: {}", target);
      return SchemaService.createSchemaAndRelatedUser(
          connection, target.getName(), target.getCharacterSet(), username, password);
    } catch (SQLException e) {
      log.error("Error while creating Schema", e);
      throw new IllegalStateException(e);
    }
  }

  private Connection getConnection() throws SQLException {
    String connectURL = format("jdbc:mysql://%1$s:%2$s", dbConfig.getHost(), dbConfig.getPort());
    log.debug("Connecting to '{}' with user '{}'", connectURL, dbConfig.getUser());
    return DriverManager.getConnection(connectURL, dbConfig.getUser(), dbConfig.getPassword());
  }

  @Override
  public void delete(MySQLSchema primary, Context<MySQLSchema> context) {
    try (Connection connection = getConnection()) {
      var userName = primary.getStatus() != null ? primary.getStatus().getUserName() : null;
      SchemaService.deleteSchemaAndRelatedUser(
          connection, primary.getMetadata().getName(), userName);
    } catch (SQLException e) {
      throw new RuntimeException("Error while trying to delete Schema", e);
    }
  }

  public static String decode(String value) {
    return new String(Base64.getDecoder().decode(value.getBytes()));
  }

  @Override
  public Set<Schema> fetchResources(MySQLSchema primaryResource) {
    try (Connection connection = getConnection()) {
      var schema =
          SchemaService.getSchema(connection, primaryResource.getMetadata().getName())
              .map(Set::of)
              .orElseGet(Collections::emptySet);
      log.debug("Fetched schema: {}", schema);
      return schema;
    } catch (SQLException e) {
      throw new RuntimeException("Error while trying read Schema", e);
    }
  }

  static class ResourcePollerConfigConverter
      implements ConfigurationConverter<SchemaConfig, ResourcePollerConfig> {

    @Override
    public ResourcePollerConfig configFrom(
        SchemaConfig configAnnotation,
        DependentResourceSpec<?, ?, ResourcePollerConfig> spec,
        ControllerConfiguration<?> parentConfiguration) {
      if (configAnnotation != null) {
        return new ResourcePollerConfig(
            Duration.ofMillis(configAnnotation.pollPeriod()),
            new MySQLDbConfig(
                configAnnotation.host(),
                String.valueOf(configAnnotation.port()),
                configAnnotation.user(),
                configAnnotation.password()));
      }
      return new ResourcePollerConfig(
          Duration.ofMillis(SchemaConfig.DEFAULT_POLL_PERIOD),
          MySQLDbConfig.loadFromEnvironmentVars());
    }
  }
}
