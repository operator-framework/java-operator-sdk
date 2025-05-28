package io.javaoperatorsdk.operator.sample;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.ClusterDeployedOperatorExtension;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.customresource.WebPage;

import static io.javaoperatorsdk.operator.sample.WebPageOperator.WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE;
import static io.javaoperatorsdk.operator.sample.WebPageOperator.WEBPAGE_RECONCILER_ENV;
import static io.javaoperatorsdk.operator.sample.WebPageReconciler.lowLevelLabel;

class WebPageOperatorE2E extends WebPageOperatorAbstractTest {

  public WebPageOperatorE2E() throws FileNotFoundException {}

  @RegisterExtension
  AbstractOperatorExtension operator =
      isLocal()
          ? LocallyRunOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withReconciler(new WebPageReconciler())
              .build()
          : ClusterDeployedOperatorExtension.builder()
              .waitForNamespaceDeletion(false)
              .withOperatorDeployment(
                  client.load(new FileInputStream("k8s/operator.yaml")).items(),
                  resources -> {
                    Deployment deployment =
                        (Deployment)
                            resources.stream()
                                .filter(r -> r instanceof Deployment)
                                .findFirst()
                                .orElseThrow();
                    Container container =
                        deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
                    if (container.getEnv() == null) {
                      container.setEnv(new ArrayList<>());
                    }
                    container
                        .getEnv()
                        .add(
                            new EnvVar(
                                WEBPAGE_RECONCILER_ENV,
                                WEBPAGE_CLASSIC_RECONCILER_ENV_VALUE,
                                null));
                  })
              .build();

  @Override
  AbstractOperatorExtension operator() {
    return operator;
  }

  @Override
  WebPage createWebPage(String title) {
    WebPage page = super.createWebPage(title);
    page.getMetadata().setLabels(lowLevelLabel());
    return page;
  }
}
