package io.javaoperatorsdk.operator.junit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Utils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;

public abstract class AbstractOperatorExtension
    implements HasKubernetesClient,
        BeforeAllCallback,
        BeforeEachCallback,
        AfterAllCallback,
        AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractOperatorExtension.class);
  public static final int MAX_NAMESPACE_NAME_LENGTH = 63;
  public static final int CRD_READY_WAIT = 2000;
  public static final int DEFAULT_NAMESPACE_DELETE_TIMEOUT = 90;

  private final KubernetesClient kubernetesClient;
  protected final List<HasMetadata> infrastructure;
  protected Duration infrastructureTimeout;
  protected final boolean oneNamespacePerClass;
  protected final boolean preserveNamespaceOnError;
  protected final boolean waitForNamespaceDeletion;
  protected final int namespaceDeleteTimeout = DEFAULT_NAMESPACE_DELETE_TIMEOUT;
  protected final Function<ExtensionContext, String> namespaceNameSupplier;
  protected final Function<ExtensionContext, String> perClassNamespaceNameSupplier;

  protected String namespace;

  protected AbstractOperatorExtension(
      List<HasMetadata> infrastructure,
      Duration infrastructureTimeout,
      boolean oneNamespacePerClass,
      boolean preserveNamespaceOnError,
      boolean waitForNamespaceDeletion,
      KubernetesClient kubernetesClient,
      Function<ExtensionContext, String> namespaceNameSupplier,
      Function<ExtensionContext, String> perClassNamespaceNameSupplier) {
    this.kubernetesClient =
        kubernetesClient != null ? kubernetesClient : new KubernetesClientBuilder().build();
    this.infrastructure = infrastructure;
    this.infrastructureTimeout = infrastructureTimeout;
    this.oneNamespacePerClass = oneNamespacePerClass;
    this.preserveNamespaceOnError = preserveNamespaceOnError;
    this.waitForNamespaceDeletion = waitForNamespaceDeletion;
    this.namespaceNameSupplier = namespaceNameSupplier;
    this.perClassNamespaceNameSupplier = perClassNamespaceNameSupplier;
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    beforeAllImpl(context);
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    beforeEachImpl(context);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    afterAllImpl(context);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    afterEachImpl(context);
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  public String getNamespace() {
    return namespace;
  }

  public <T extends HasMetadata>
      NonNamespaceOperation<T, KubernetesResourceList<T>, Resource<T>> resources(Class<T> type) {
    return kubernetesClient.resources(type).inNamespace(namespace);
  }

  public <T extends HasMetadata> T get(Class<T> type, String name) {
    return kubernetesClient.resources(type).inNamespace(namespace).withName(name).get();
  }

  public <T extends HasMetadata> T create(T resource) {
    return kubernetesClient.resource(resource).inNamespace(namespace).create();
  }

  public <T extends HasMetadata> T replace(T resource) {
    return kubernetesClient.resource(resource).inNamespace(namespace).replace();
  }

  public <T extends HasMetadata> boolean delete(T resource) {
    var res = kubernetesClient.resource(resource).inNamespace(namespace).delete();
    return res.size() == 1 && res.get(0).getCauses().isEmpty();
  }

  protected void beforeAllImpl(ExtensionContext context) {
    if (oneNamespacePerClass) {
      namespace = perClassNamespaceNameSupplier.apply(context);
      before(context);
    }
  }

  protected void beforeEachImpl(ExtensionContext context) {
    if (!oneNamespacePerClass) {
      namespace = namespaceNameSupplier.apply(context);
      before(context);
    }
  }

  protected void before(ExtensionContext context) {
    LOGGER.info("Initializing integration test in namespace {}", namespace);

    kubernetesClient
        .namespaces()
        .resource(
            new NamespaceBuilder()
                .withMetadata(new ObjectMetaBuilder().withName(namespace).build())
                .build())
        .serverSideApply();

    kubernetesClient.resourceList(infrastructure).serverSideApply();
    kubernetesClient
        .resourceList(infrastructure)
        .waitUntilReady(infrastructureTimeout.toMillis(), TimeUnit.MILLISECONDS);
  }

  protected void afterAllImpl(ExtensionContext context) {
    if (oneNamespacePerClass) {
      after(context);
    }
  }

  protected void afterEachImpl(ExtensionContext context) {
    if (!oneNamespacePerClass) {
      after(context);
    }
  }

  protected void after(ExtensionContext context) {
    if (namespace != null) {
      if (preserveNamespaceOnError && context.getExecutionException().isPresent()) {
        LOGGER.info("Preserving namespace {}", namespace);
      } else {
        kubernetesClient.resourceList(infrastructure).delete();
        deleteOperator();
        LOGGER.info("Deleting namespace {} and stopping operator", namespace);
        kubernetesClient.namespaces().withName(namespace).delete();
        if (waitForNamespaceDeletion) {
          LOGGER.info("Waiting for namespace {} to be deleted", namespace);
          Awaitility.await("namespace deleted")
              .pollInterval(50, TimeUnit.MILLISECONDS)
              .atMost(namespaceDeleteTimeout, TimeUnit.SECONDS)
              .until(() -> kubernetesClient.namespaces().withName(namespace).get() == null);
        }
      }
    }
  }

  protected void deleteOperator() {
    // nothing to do by default: only needed if the operator is deployed to the cluster
  }

  @SuppressWarnings("unchecked")
  public abstract static class AbstractBuilder<T extends AbstractBuilder<T>> {
    protected final List<HasMetadata> infrastructure;
    protected Duration infrastructureTimeout;
    protected boolean preserveNamespaceOnError;
    protected boolean waitForNamespaceDeletion;
    protected boolean oneNamespacePerClass;
    protected int namespaceDeleteTimeout;
    protected Consumer<ConfigurationServiceOverrider> configurationServiceOverrider;
    protected Function<ExtensionContext, String> namespaceNameSupplier =
        new DefaultNamespaceNameSupplier();
    protected Function<ExtensionContext, String> perClassNamespaceNameSupplier =
        new DefaultPerClassNamespaceNameSupplier();

    protected AbstractBuilder() {
      this.infrastructure = new ArrayList<>();
      this.infrastructureTimeout = Duration.ofMinutes(1);

      this.preserveNamespaceOnError =
          Utils.getSystemPropertyOrEnvVar("josdk.it.preserveNamespaceOnError", false);

      this.waitForNamespaceDeletion =
          Utils.getSystemPropertyOrEnvVar("josdk.it.waitForNamespaceDeletion", true);

      this.oneNamespacePerClass =
          Utils.getSystemPropertyOrEnvVar("josdk.it.oneNamespacePerClass", false);

      this.namespaceDeleteTimeout =
          Utils.getSystemPropertyOrEnvVar(
              "josdk.it.namespaceDeleteTimeout", DEFAULT_NAMESPACE_DELETE_TIMEOUT);
    }

    public T preserveNamespaceOnError(boolean value) {
      this.preserveNamespaceOnError = value;
      return (T) this;
    }

    public T waitForNamespaceDeletion(boolean value) {
      this.waitForNamespaceDeletion = value;
      return (T) this;
    }

    public T oneNamespacePerClass(boolean value) {
      this.oneNamespacePerClass = value;
      return (T) this;
    }

    public T withConfigurationService(Consumer<ConfigurationServiceOverrider> overrider) {
      configurationServiceOverrider = overrider;
      return (T) this;
    }

    public T withInfrastructureTimeout(Duration value) {
      infrastructureTimeout = value;
      return (T) this;
    }

    public T withInfrastructure(List<HasMetadata> hm) {
      infrastructure.addAll(hm);
      return (T) this;
    }

    public T withInfrastructure(HasMetadata... hms) {
      infrastructure.addAll(Arrays.asList(hms));
      return (T) this;
    }

    public T withNamespaceDeleteTimeout(int timeout) {
      this.namespaceDeleteTimeout = timeout;
      return (T) this;
    }

    public AbstractBuilder<T> withNamespaceNameSupplier(
        Function<ExtensionContext, String> namespaceNameSupplier) {
      this.namespaceNameSupplier = namespaceNameSupplier;
      return this;
    }

    public AbstractBuilder<T> withPerClassNamespaceNameSupplier(
        Function<ExtensionContext, String> perClassNamespaceNameSupplier) {
      this.perClassNamespaceNameSupplier = perClassNamespaceNameSupplier;
      return this;
    }
  }
}
