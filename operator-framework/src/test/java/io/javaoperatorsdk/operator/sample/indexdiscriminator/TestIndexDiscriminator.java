package io.javaoperatorsdk.operator.sample.indexdiscriminator;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.IndexDiscriminator;

import static io.javaoperatorsdk.operator.sample.indexdiscriminator.IndexDiscriminatorTestReconciler.configMapKeyFromPrimary;

public class TestIndexDiscriminator
    extends IndexDiscriminator<ConfigMap, IndexDiscriminatorTestCustomResource> {

  public TestIndexDiscriminator(String indexName, String nameSuffix) {
    super(indexName, p -> configMapKeyFromPrimary(p, nameSuffix));
  }
}
