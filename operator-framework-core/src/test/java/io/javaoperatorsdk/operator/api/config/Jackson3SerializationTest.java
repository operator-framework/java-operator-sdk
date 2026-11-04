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
package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.fabric8.kubernetes.model.jackson.Jackson2JdkTypesModule;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.matcher.JsonMergePatchMatcher;
import io.javaoperatorsdk.operator.processing.matcher.JsonPatchMatcher;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.cfg.ContextAttributes;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Jackson3SerializationTest {

  private static final String PREFIX_ATTRIBUTE = "valuePrefix";
  private static final String PREFIX = "custom:";

  private KubernetesClient client;
  private ConfigurationService configuration;

  @BeforeEach
  void setup() {
    var mapper =
        JsonMapper.builderWithJackson2Defaults()
            .addModule(new Jackson2JdkTypesModule())
            .defaultAttributes(
                ContextAttributes.getEmpty().withSharedAttribute(PREFIX_ATTRIBUTE, PREFIX))
            .build();
    client =
        new KubernetesClientBuilder()
            .withKubernetesSerialization(new KubernetesSerialization(mapper, true))
            .build();
    configuration =
        new ConfigurationServiceOverrider(new BaseConfigurationService())
            .withKubernetesClient(client)
            .build();
  }

  @AfterEach
  void cleanup() {
    client.close();
  }

  @Test
  void configuredClientClonesCustomResourceUsingJackson3Annotations() {
    var original = resource("value");
    var node =
        configuration
            .getKubernetesClient()
            .getKubernetesSerialization()
            .convertValue(original, JsonNode.class);

    assertThat(configuration.getKubernetesClient()).isSameAs(client);
    assertThat(node.at("/spec/value").asString()).isEqualTo("custom:value");
    assertThat(node.at("/spec/timeout").asString()).isEqualTo("PT30S");

    var cloned = configuration.getResourceCloner().clone(original);
    assertThat(cloned).isNotSameAs(original);
    assertThat(cloned.getSpec()).isNotSameAs(original.getSpec()).isEqualTo(original.getSpec());
    assertThat(cloned.getMetadata()).isNotSameAs(original.getMetadata());
    assertThat(cloned.getMetadata().getName()).isEqualTo(original.getMetadata().getName());
  }

  @Test
  void matchersUseConfiguredSerializationRatherThanPojoEquality() {
    Context<SerializationCustomResource> context = mock();
    when(context.getClient()).thenReturn(configuration.getKubernetesClient());
    var desired = resource("VALUE");
    var actual = configuration.getResourceCloner().clone(desired);

    assertThat(desired.getSpec()).isNotEqualTo(actual.getSpec());
    assertThat(JsonPatchMatcher.getInstance().matches(desired, actual, context)).isTrue();
    assertThat(JsonMergePatchMatcher.getInstance().matches(desired, actual, context)).isTrue();

    var changed = resource("different");
    assertThat(JsonPatchMatcher.getInstance().matches(changed, actual, context)).isFalse();
    assertThat(JsonMergePatchMatcher.getInstance().matches(changed, actual, context)).isFalse();
  }

  private static SerializationCustomResource resource(String value) {
    var resource = new SerializationCustomResource();
    resource.getMetadata().setName("serialization-test");
    resource.setSpec(new SerializationSpec(new EncodedValue(value), Duration.ofSeconds(30)));
    return resource;
  }

  @Group("serialization.javaoperatorsdk.io")
  @Version("v1")
  public static class SerializationCustomResource extends CustomResource<SerializationSpec, Void> {}

  public record SerializationSpec(EncodedValue value, Duration timeout) {}

  @JsonSerialize(using = EncodedValueSerializer.class)
  @JsonDeserialize(using = EncodedValueDeserializer.class)
  public record EncodedValue(String value) {}

  public static class EncodedValueSerializer extends ValueSerializer<EncodedValue> {
    @Override
    public void serialize(
        EncodedValue value, JsonGenerator generator, SerializationContext context) {
      var prefix = (String) Objects.requireNonNull(context.getAttribute(PREFIX_ATTRIBUTE));
      generator.writeString(prefix + value.value().toLowerCase(Locale.ROOT));
    }
  }

  public static class EncodedValueDeserializer extends ValueDeserializer<EncodedValue> {
    @Override
    public EncodedValue deserialize(JsonParser parser, DeserializationContext context) {
      var prefix = (String) Objects.requireNonNull(context.getAttribute(PREFIX_ATTRIBUTE));
      return new EncodedValue(parser.getString().substring(prefix.length()));
    }
  }
}
