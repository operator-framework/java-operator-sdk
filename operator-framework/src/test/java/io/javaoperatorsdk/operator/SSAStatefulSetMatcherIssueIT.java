package io.javaoperatorsdk.operator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.ssastatefulsetmatcherissue.SSAStatefulSetMatcherIssueCustomResource;
import io.javaoperatorsdk.operator.sample.ssastatefulsetmatcherissue.SSAStatefulSetMatcherIssueReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class SSAStatefulSetMatcherIssueIT {

  public static final String TEST_1 = "test1";
  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new SSAStatefulSetMatcherIssueReconciler())
          .build();

  @Test
  void testSSAMatcher() {
    extension.create(testResource());


    await().untilAsserted(() -> {
      var statefulSet = extension.get(StatefulSet.class, TEST_1);
      assertThat(statefulSet).isNotNull();
    });
  }

  SSAStatefulSetMatcherIssueCustomResource testResource() {
    var res = new SSAStatefulSetMatcherIssueCustomResource();
    res.setMetadata(new ObjectMetaBuilder()
        .withName(TEST_1)
        .build());
    return res;
  }

}
