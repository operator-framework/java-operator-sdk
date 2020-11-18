package io.javaoperatorsdk.operator.config;

import java.util.Optional;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;

public class Configuration {
    private final ClientConfiguration client = new ClientConfiguration();
    private final OperatorConfiguration operator = new OperatorConfiguration();
    private final RetryConfiguration retry = new RetryConfiguration();
    
    public ClientConfiguration getClientConfiguration() {
        return client;
    }
    
    public OperatorConfiguration getOperatorConfiguration() {
        return operator;
    }
    
    public RetryConfiguration getRetryConfiguration() {
        return retry;
    }
    
    public KubernetesClient getConfiguredClient() {
        return getClientFor(getClientConfiguration(), getOperatorConfiguration());
    }
    
    public static KubernetesClient getClientFor(ClientConfiguration clientCfg, OperatorConfiguration operatorCfg) {
        ConfigBuilder cb = new ConfigBuilder();
        
        cb.withTrustCerts(clientCfg.isTrustSelfSignedCertificates());
        trimmedPropertyIfPresent(clientCfg.getUsername()).ifPresent(cb::withUsername);
        trimmedPropertyIfPresent(clientCfg.getPassword()).ifPresent(cb::withPassword);
        trimmedPropertyIfPresent(clientCfg.getMasterUrl()).ifPresent(cb::withMasterUrl);
        
        operatorCfg.getWatchedNamespaceIfUnique().ifPresent(cb::withNamespace);
        return clientCfg.isOpenshift() ? new DefaultOpenShiftClient(cb.build()) : new DefaultKubernetesClient(cb.build());
    }
    
    public static Retry getRetryFor(RetryConfiguration retryCfg) {
        GenericRetry retry = new GenericRetry();
        Optional.ofNullable(retryCfg.getInitialInterval()).ifPresent(retry::setInitialInterval);
        Optional.ofNullable(retryCfg.getIntervalMultiplier()).ifPresent(retry::setIntervalMultiplier);
        Optional.ofNullable(retryCfg.getMaxAttempts()).ifPresent(retry::setMaxAttempts);
        Optional.ofNullable(retryCfg.getMaxInterval()).ifPresent(retry::setMaxInterval);
        return retry;
    }
    
    private static Optional<String> trimmedPropertyIfPresent(String string) {
        return Optional.ofNullable(string).map(String::trim);
    }
    
    public static Configuration defaultConfiguration() {
        return new Configuration();
    }
}
