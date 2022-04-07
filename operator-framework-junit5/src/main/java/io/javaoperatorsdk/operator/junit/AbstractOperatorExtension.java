package io.javaoperatorsdk.operator.junit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.extension.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.fabric8.kubernetes.client.utils.Utils;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;

public abstract class AbstractOperatorExtension implements HasKubernetesClient,
    BeforeAllCallback,
    BeforeEachCallback,
    AfterAllCallback,
    AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractOperatorExtension.class);
  public static final int CRD_READY_WAIT = 2000;

  private final KubernetesClient kubernetesClient;
  protected final ConfigurationService configurationService;
  protected final List<HasMetadata> infrastructure;
  protected Duration infrastructureTimeout;
  protected final boolean oneNamespacePerClass;
  protected final boolean preserveNamespaceOnError;
  protected final boolean waitForNamespaceDeletion;

  protected String namespace;

  protected AbstractOperatorExtension(
      ConfigurationService configurationService,
      List<HasMetadata> infrastructure,
      Duration infrastructureTimeout,
      boolean oneNamespacePerClass,
      boolean preserveNamespaceOnError,
      boolean waitForNamespaceDeletion) {

    this.kubernetesClient = new DefaultKubernetesClient();
    this.configurationService = configurationService;
    this.infrastructure = infrastructure;
    this.infrastructureTimeout = infrastructureTimeout;
    this.oneNamespacePerClass = oneNamespacePerClass;
    this.preserveNamespaceOnError = preserveNamespaceOnError;
    this.waitForNamespaceDeletion = waitForNamespaceDeletion;
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

  public <T extends HasMetadata> NonNamespaceOperation<T, KubernetesResourceList<T>, Resource<T>> resources(
      Class<T> type) {
    return kubernetesClient.resources(type).inNamespace(namespace);
  }

  public <T extends HasMetadata> T get(Class<T> type, String name) {
    return kubernetesClient.resources(type).inNamespace(namespace).withName(name).get();
  }

  public <T extends HasMetadata> T create(Class<T> type, T resource) {
    return kubernetesClient.resources(type).inNamespace(namespace).create(resource);
  }

  public <T extends HasMetadata> T replace(Class<T> type, T resource) {
    return kubernetesClient.resources(type).inNamespace(namespace).replace(resource);
  }

  @SuppressWarnings("unchecked")
  public <T extends HasMetadata> boolean delete(Class<T> type, T resource) {
    return kubernetesClient.resources(type).inNamespace(namespace).delete(resource);
  }

  protected void beforeAllImpl(ExtensionContext context) {
    if (oneNamespacePerClass) {
      namespace = context.getRequiredTestClass().getSimpleName();
      namespace += "-";
      namespace += UUID.randomUUID();
      namespace = KubernetesResourceUtil.sanitizeName(namespace).toLowerCase(Locale.US);
      namespace = namespace.substring(0, Math.min(namespace.length(), 63));

      before(context);
    }
  }

  protected void beforeEachImpl(ExtensionContext context) {
    if (!oneNamespacePerClass) {
      namespace = context.getRequiredTestClass().getSimpleName();
      namespace += "-";
      namespace += context.getRequiredTestMethod().getName();
      namespace += "-";
      namespace += UUID.randomUUID();
      namespace = KubernetesResourceUtil.sanitizeName(namespace).toLowerCase(Locale.US);
      namespace = namespace.substring(0, Math.min(namespace.length(), 63));

      before(context);
    }
  }

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
              .atMost(90, TimeUnit.SECONDS)
              .until(() -> kubernetesClient.namespaces().withName(namespace).get() == null);
        }
      }
    }
  }

  protected void deleteOperator() {
    // nothing to do by default: only needed if the operator is deployed to the cluster
  }

  @SuppressWarnings("unchecked")
  public static abstract class AbstractBuilder<T extends AbstractBuilder<T>> {
    protected ConfigurationService configurationService;
    protected final List<HasMetadata> infrastructure;
    protected Duration infrastructureTimeout;
    protected boolean preserveNamespaceOnError;
    protected boolean waitForNamespaceDeletion;
    protected boolean oneNamespacePerClass;

    protected AbstractBuilder() {
      this.configurationService = ConfigurationServiceProvider.instance();

      this.infrastructure = new ArrayList<>();
      this.infrastructureTimeout = Duration.ofMinutes(1);

      this.preserveNamespaceOnError = Utils.getSystemPropertyOrEnvVar(
          "josdk.it.preserveNamespaceOnError",
          false);

      this.waitForNamespaceDeletion = Utils.getSystemPropertyOrEnvVar(
          "josdk.it.waitForNamespaceDeletion",
          true);

      this.oneNamespacePerClass = Utils.getSystemPropertyOrEnvVar(
          "josdk.it.oneNamespacePerClass",
          false);
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

    public T withConfigurationService(ConfigurationService value) {
      configurationService = value;
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

  }
}
