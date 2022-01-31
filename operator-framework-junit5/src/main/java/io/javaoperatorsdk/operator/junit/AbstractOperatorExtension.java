package io.javaoperatorsdk.operator.junit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.extension.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.KubernetesResourceUtil;
import io.fabric8.kubernetes.client.utils.Utils;
import io.javaoperatorsdk.operator.api.config.BaseConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.Version;

public abstract class AbstractOperatorExtension implements HasKubernetesClient,
    BeforeAllCallback,
    BeforeEachCallback,
    AfterAllCallback,
    AfterEachCallback {

  protected final KubernetesClient kubernetesClient;
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
  public void beforeAll(ExtensionContext context) throws Exception {
    beforeAllImpl(context);
  }

  @Override
  public void beforeEach(ExtensionContext context) throws Exception {
    beforeEachImpl(context);
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    afterAllImpl(context);
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
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

  protected abstract void before(ExtensionContext context);

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

  protected abstract void after(ExtensionContext context);

  public static abstract class AbstractBuilder {
    protected ConfigurationService configurationService;
    protected final List<HasMetadata> infrastructure;
    protected Duration infrastructureTimeout;
    protected boolean preserveNamespaceOnError;
    protected boolean waitForNamespaceDeletion;
    protected boolean oneNamespacePerClass;

    protected AbstractBuilder() {
      this.configurationService = new BaseConfigurationService(Version.UNKNOWN);

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
  }
}
