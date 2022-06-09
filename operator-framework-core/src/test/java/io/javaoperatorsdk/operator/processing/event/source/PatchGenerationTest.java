package io.javaoperatorsdk.operator.processing.event.source;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.config.*;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResourceStatus;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true, https = false)
class PatchGenerationTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(PatchGenerationTest.class);
  public static final String NAMESPACE = "test";

  KubernetesClient client;

  @Test
  void reproducePatchIssueWithMockServer() {
    var created =
        client.resources(TestCustomResource.class).inNamespace(NAMESPACE)
            .create(newMyResource("foo", NAMESPACE));
    var createdWithStatusChanged = ConfigurationService.DEFAULT_CLONER.clone(created);
    createdWithStatusChanged.setStatus(new TestCustomResourceStatus());
    createdWithStatusChanged.getStatus().setConfigMapStatus("somevalue");

    var res = patchStatus(createdWithStatusChanged, created);

    assertThat(res.getMetadata().getGeneration()).isEqualTo(created.getMetadata().getGeneration());
  }

  public TestCustomResource patchStatus(TestCustomResource resource,
      TestCustomResource originalResource) {

    String resourceVersion = resource.getMetadata().getResourceVersion();
    // don't do optimistic locking on patch
    originalResource.getMetadata().setResourceVersion(null);
    resource.getMetadata().setResourceVersion(null);
    try (var bis = new ByteArrayInputStream(
        Serialization.asJson(originalResource).getBytes(StandardCharsets.UTF_8))) {
      return client.resources(TestCustomResource.class)
          .inNamespace(NAMESPACE)
          // will be simplified in fabric8 v6
          .load(bis)
          .editStatus(r -> resource);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } finally {
      // restore initial resource version
      originalResource.getMetadata().setResourceVersion(resourceVersion);
      resource.getMetadata().setResourceVersion(resourceVersion);
    }
  }

  public TestCustomResource newMyResource(String app, String namespace) {
    TestCustomResource resource = new TestCustomResource();
    resource.setMetadata(new ObjectMetaBuilder().withName(app).addToLabels("app", app).build());
    resource.getMetadata().setNamespace(namespace);
    return resource;
  }
}
