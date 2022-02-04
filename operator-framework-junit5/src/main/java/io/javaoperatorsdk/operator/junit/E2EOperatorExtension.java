package io.javaoperatorsdk.operator.junit;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;

public class E2EOperatorExtension extends AbstractOperatorExtension {

  private static final Logger LOGGER = LoggerFactory.getLogger(E2EOperatorExtension.class);

  private final List<HasMetadata> operatorDeployment;
  private final Duration operatorDeploymentTimeout;

  private E2EOperatorExtension(
      ConfigurationService configurationService,
      List<HasMetadata> operatorDeployment,
      Duration operatorDeploymentTimeout,
      List<HasMetadata> infrastructure,
      Duration infrastructureTimeout,
      boolean preserveNamespaceOnError,
      boolean waitForNamespaceDeletion,
      boolean oneNamespacePerClass) {
    super(configurationService, infrastructure, infrastructureTimeout, oneNamespacePerClass,
        preserveNamespaceOnError,
        waitForNamespaceDeletion);
    this.operatorDeployment = operatorDeployment;
    this.operatorDeploymentTimeout = operatorDeploymentTimeout;
  }

  /**
   * Creates a {@link Builder} to set up an {@link E2EOperatorExtension} instance.
   *
   * @return the builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  @SuppressWarnings("unchecked")
  protected void before(ExtensionContext context) {
    LOGGER.info("Initializing integration test in namespace {}", namespace);

    kubernetesClient
        .namespaces()
        .create(new NamespaceBuilder().withNewMetadata().withName(namespace).endMetadata().build());

    kubernetesClient
        .resourceList(infrastructure)
        .createOrReplace();
    kubernetesClient
        .resourceList(infrastructure)
        .waitUntilReady(infrastructureTimeout.toMillis(), TimeUnit.MILLISECONDS);

    final var crdPath = "./target/classes/META-INF/fabric8/";
    final var crdSuffix = "-v1.yml";

    for (var crdFile : new File(crdPath).listFiles((ignored, name) -> name.endsWith(crdSuffix))) {
      try (InputStream is = new FileInputStream(crdFile)) {
        final var crd = kubernetesClient.load(is);
        crd.createOrReplace();
        crd.waitUntilReady(2, TimeUnit.SECONDS);
        LOGGER.debug("Applied CRD with name: {}", crd.get().get(0).getMetadata().getName());
      } catch (Exception ex) {
        throw new IllegalStateException("Cannot apply CRD yaml: " + crdFile.getAbsolutePath(), ex);
      }
    }

    LOGGER.debug("Deploying the operator into Kubernetes");
    operatorDeployment.stream().forEach(hm -> {
      hm.getMetadata().setNamespace(namespace);
      if (hm.getKind().toLowerCase(Locale.ROOT).equals("clusterrolebinding")) {
        var crb = (ClusterRoleBinding) hm;
        for (var subject : crb.getSubjects()) {
          subject.setNamespace(namespace);
        }
      }
    });

    kubernetesClient
        .resourceList(operatorDeployment)
        .inNamespace(namespace)
        .createOrReplace();
    kubernetesClient
        .resourceList(operatorDeployment)
        .waitUntilReady(operatorDeploymentTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  protected void after(ExtensionContext context) {
    if (namespace != null) {
      if (preserveNamespaceOnError && context.getExecutionException().isPresent()) {
        LOGGER.info("Preserving namespace {}", namespace);
      } else {
        kubernetesClient.resourceList(infrastructure).delete();
        kubernetesClient.resourceList(operatorDeployment).inNamespace(namespace).delete();
        LOGGER.info("Deleting namespace {} and stopping operator", namespace);
        kubernetesClient.namespaces().withName(namespace).delete();
        if (waitForNamespaceDeletion) {
          LOGGER.info("Waiting for namespace {} to be deleted", namespace);
          Awaitility.await("namespace deleted")
              .pollInterval(50, TimeUnit.MILLISECONDS)
              .atMost(90, TimeUnit.SECONDS)
              .until(() -> kubernetesClient.namespaces().withName(namespace).get() == null);
        }
      }
    }
  }

  @SuppressWarnings("rawtypes")
  public static class Builder extends AbstractBuilder {
    private final List<HasMetadata> operatorDeployment;
    private Duration deploymentTimeout;

    protected Builder() {
      super();;
      this.operatorDeployment = new ArrayList<>();
      this.deploymentTimeout = Duration.ofMinutes(1);
    }

    public Builder preserveNamespaceOnError(boolean value) {
      this.preserveNamespaceOnError = value;
      return this;
    }

    public Builder waitForNamespaceDeletion(boolean value) {
      this.waitForNamespaceDeletion = value;
      return this;
    }

    public Builder oneNamespacePerClass(boolean value) {
      this.oneNamespacePerClass = value;
      return this;
    }

    public Builder withConfigurationService(ConfigurationService value) {
      configurationService = value;
      return this;
    }

    public Builder withDeploymentTimeout(Duration value) {
      deploymentTimeout = value;
      return this;
    }

    public Builder withInfrastructureTimeout(Duration value) {
      infrastructureTimeout = value;
      return this;
    }

    public Builder withInfrastructure(List<HasMetadata> hm) {
      infrastructure.addAll(hm);
      return this;
    }

    public Builder withInfrastructure(HasMetadata... hms) {
      for (HasMetadata hm : hms) {
        infrastructure.add(hm);
      }
      return this;
    }

    public Builder withOperatorDeployment(List<HasMetadata> hm) {
      operatorDeployment.addAll(hm);
      return this;
    }

    public Builder withOperatorDeployment(HasMetadata... hms) {
      for (HasMetadata hm : hms) {
        operatorDeployment.add(hm);
      }
      return this;
    }

    public E2EOperatorExtension build() {
      return new E2EOperatorExtension(
          configurationService,
          operatorDeployment,
          deploymentTimeout,
          infrastructure,
          infrastructureTimeout,
          preserveNamespaceOnError,
          waitForNamespaceDeletion,
          oneNamespacePerClass);
    }
  }
}
