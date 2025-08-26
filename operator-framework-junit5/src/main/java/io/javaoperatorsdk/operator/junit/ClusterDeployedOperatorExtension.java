package io.javaoperatorsdk.operator.junit;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

public class ClusterDeployedOperatorExtension extends AbstractOperatorExtension {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ClusterDeployedOperatorExtension.class);

  private final List<HasMetadata> operatorDeployment;
  private final Duration operatorDeploymentTimeout;

  private ClusterDeployedOperatorExtension(
      List<HasMetadata> operatorDeployment,
      Duration operatorDeploymentTimeout,
      List<HasMetadata> infrastructure,
      Duration infrastructureTimeout,
      boolean preserveNamespaceOnError,
      boolean waitForNamespaceDeletion,
      boolean oneNamespacePerClass,
      KubernetesClient kubernetesClient,
      KubernetesClient infrastructureKubernetesClient,
      Function<ExtensionContext, String> namespaceNameSupplier,
      Function<ExtensionContext, String> perClassNamespaceNameSupplier) {
    super(
        infrastructure,
        infrastructureTimeout,
        oneNamespacePerClass,
        preserveNamespaceOnError,
        waitForNamespaceDeletion,
        kubernetesClient,
        infrastructureKubernetesClient,
        namespaceNameSupplier,
        perClassNamespaceNameSupplier);
    this.operatorDeployment = operatorDeployment;
    this.operatorDeploymentTimeout = operatorDeploymentTimeout;
  }

  /**
   * Creates a {@link Builder} to set up an {@link ClusterDeployedOperatorExtension} instance.
   *
   * @return the builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  protected void before(ExtensionContext context) {
    super.before(context);

    final var crdPath = "./target/classes/META-INF/fabric8/";
    final var crdSuffix = "-v1.yml";

    final var kubernetesClient = getInfrastructureKubernetesClient();
    for (var crdFile :
        Objects.requireNonNull(
            new File(crdPath).listFiles((ignored, name) -> name.endsWith(crdSuffix)))) {
      try (InputStream is = new FileInputStream(crdFile)) {
        final var crd = kubernetesClient.load(is);
        crd.createOrReplace();
        Thread.sleep(CRD_READY_WAIT); // readiness is not applicable for CRD, just wait a little
        LOGGER.debug("Applied CRD with name: {}", crd.get().get(0).getMetadata().getName());
      } catch (InterruptedException ex) {
        LOGGER.error("Interrupted.", ex);
        Thread.currentThread().interrupt();
      } catch (Exception ex) {
        throw new IllegalStateException("Cannot apply CRD yaml: " + crdFile.getAbsolutePath(), ex);
      }
    }

    LOGGER.debug("Deploying the operator into Kubernetes. Target namespace: {}", namespace);
    operatorDeployment.forEach(
        hm -> {
          hm.getMetadata().setNamespace(namespace);
          if (hm.getKind().toLowerCase(Locale.ROOT).equals("clusterrolebinding")) {
            var crb = (ClusterRoleBinding) hm;
            for (var subject : crb.getSubjects()) {
              subject.setNamespace(namespace);
            }
          }
        });

    kubernetesClient.resourceList(operatorDeployment).inNamespace(namespace).createOrReplace();
    kubernetesClient
        .resourceList(operatorDeployment)
        .waitUntilReady(operatorDeploymentTimeout.toMillis(), TimeUnit.MILLISECONDS);
    LOGGER.debug("Operator resources deployed.");
  }

  @Override
  protected void deleteOperator() {
    getInfrastructureKubernetesClient()
        .resourceList(operatorDeployment)
        .inNamespace(namespace)
        .delete();
  }

  public static class Builder extends AbstractBuilder<Builder> {
    private final List<HasMetadata> operatorDeployment;
    private Duration deploymentTimeout;
    private KubernetesClient kubernetesClient;
    private KubernetesClient infrastructureKubernetesClient;

    protected Builder() {
      super();
      this.operatorDeployment = new ArrayList<>();
      this.deploymentTimeout = Duration.ofMinutes(1);
    }

    @SuppressWarnings("unused")
    public Builder withDeploymentTimeout(Duration value) {
      deploymentTimeout = value;
      return this;
    }

    public Builder withOperatorDeployment(
        List<HasMetadata> hm, Consumer<List<HasMetadata>> modifications) {
      modifications.accept(hm);
      operatorDeployment.addAll(hm);
      return this;
    }

    public Builder withOperatorDeployment(List<HasMetadata> hm) {
      operatorDeployment.addAll(hm);
      return this;
    }

    @SuppressWarnings("unused")
    public Builder withOperatorDeployment(HasMetadata... hms) {
      operatorDeployment.addAll(Arrays.asList(hms));
      return this;
    }

    public Builder withKubernetesClient(KubernetesClient kubernetesClient) {
      this.kubernetesClient = kubernetesClient;
      return this;
    }

    public Builder withInfrastructureKubernetesClient(KubernetesClient kubernetesClient) {
      this.infrastructureKubernetesClient = kubernetesClient;
      return this;
    }

    public ClusterDeployedOperatorExtension build() {
      infrastructureKubernetesClient =
          infrastructureKubernetesClient != null
              ? infrastructureKubernetesClient
              : new KubernetesClientBuilder().build();
      kubernetesClient =
          kubernetesClient != null ? kubernetesClient : infrastructureKubernetesClient;
      return new ClusterDeployedOperatorExtension(
          operatorDeployment,
          deploymentTimeout,
          infrastructure,
          infrastructureTimeout,
          preserveNamespaceOnError,
          waitForNamespaceDeletion,
          oneNamespacePerClass,
          kubernetesClient,
          infrastructureKubernetesClient,
          namespaceNameSupplier,
          perClassNamespaceNameSupplier);
    }
  }
}
