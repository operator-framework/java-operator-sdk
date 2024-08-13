package io.javaoperatorsdk.operator.baseapi.informerremotecluster;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.javaoperatorsdk.jenvtest.junit.EnableKubeAPIServer;
import io.javaoperatorsdk.operator.baseapi.labelselector.LabelSelectorTestReconciler;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

@EnableKubeAPIServer(apiServerFlags = {"--min-request-timeout", "1"})
public class InformerRemoteClusterIT {

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new LabelSelectorTestReconciler())
          .build();

  @Test
  void testRemoteClusterInformer() {

  }

}
