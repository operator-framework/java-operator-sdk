package io.javaoperatorsdk.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.GenericKubernetesDependentSpec;
import io.javaoperatorsdk.operator.sample.generickubernetesresource.generickubernetesdependentstandalone.ConfigMapGenericKubernetesDependent;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public abstract class GenericKubernetesDependentTestBase<R extends CustomResource<GenericKubernetesDependentSpec, Void>> {

  public static final String INITIAL_DATA = "Initial data";
  public static final String CHANGED_DATA = "Changed data";
  public static final String TEST_RESOURCE_NAME = "test1";

  @Test
  void testReconciliation() {
    var resource = extension().create(testResource(TEST_RESOURCE_NAME, INITIAL_DATA));

    await().untilAsserted(() -> {
      var cm = extension().get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(cm).isNotNull();
      assertThat(cm.getData()).containsEntry(ConfigMapGenericKubernetesDependent.KEY, INITIAL_DATA);
    });

    resource.getSpec().setValue(CHANGED_DATA);
    resource = extension().replace(resource);

    await().untilAsserted(() -> {
      var cm = extension().get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(cm.getData()).containsEntry(ConfigMapGenericKubernetesDependent.KEY, CHANGED_DATA);
    });

    extension().delete(resource);

    await().timeout(Duration.ofSeconds(30)).untilAsserted(() -> {
      var cm = extension().get(ConfigMap.class, TEST_RESOURCE_NAME);
      assertThat(cm).isNull();
    });
  }

  public abstract LocallyRunOperatorExtension extension();

  abstract R testResource(String name, String data);

}
