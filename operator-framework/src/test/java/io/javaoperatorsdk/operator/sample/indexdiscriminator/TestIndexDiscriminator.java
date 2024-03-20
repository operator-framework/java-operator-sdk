package io.javaoperatorsdk.operator.sample.indexdiscriminator;

import static io.javaoperatorsdk.operator.sample.indexdiscriminator.IndexDiscriminatorTestReconciler.configMapKeyFromPrimary;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.IndexDiscriminator;

public class TestIndexDiscriminator
    extends IndexDiscriminator<ConfigMap, IndexDiscriminatorTestCustomResource> {

  public TestIndexDiscriminator(String indexName, String nameSuffix) {
    super(indexName, p -> configMapKeyFromPrimary(p, nameSuffix));
  }
}
