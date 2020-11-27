package io.javaoperatorsdk.operator.springboot.starter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.config.AnnotationConfiguration;
import io.javaoperatorsdk.operator.config.ClientConfiguration;
import io.javaoperatorsdk.operator.config.ConfigurationService;
import io.javaoperatorsdk.operator.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.config.RetryConfiguration;
import io.javaoperatorsdk.operator.processing.retry.GenericRetry;
import io.javaoperatorsdk.operator.processing.retry.Retry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ConfigurationProperties.class})
public class OperatorAutoConfiguration implements ConfigurationService {
    private static final Logger log = LoggerFactory.getLogger(OperatorAutoConfiguration.class);
    @Autowired
    private ConfigurationProperties configuration;
    private final Map<String, ControllerConfiguration> controllers = new ConcurrentHashMap<>();
    
    @Bean
    @ConditionalOnMissingBean
    public KubernetesClient kubernetesClient() {
        final var clientCfg = getClientConfiguration();
    ConfigBuilder config = new ConfigBuilder();
    config.withTrustCerts(clientCfg.isTrustSelfSignedCertificates());
    clientCfg.getMasterUrl().ifPresent(config::withMasterUrl);
        clientCfg.getUsername().ifPresent(config::withUsername);
    clientCfg.getPassword().ifPresent(config::withPassword);
    return clientCfg.isOpenshift() ? new DefaultOpenShiftClient(config.build()) : new DefaultKubernetesClient(config.build());
    }
    
    @Bean
    @ConditionalOnMissingBean(Operator.class)
    public Operator operator(KubernetesClient kubernetesClient, ConfigurationProperties config, List<ResourceController> resourceControllers) {
        Operator operator = new Operator(kubernetesClient);
        // todo: create register method that takes the controller's configuration into account
    resourceControllers.forEach(r -> operator.registerController(processController(r)));
        return operator;
    }
    
    private ResourceController processController(ResourceController controller) {
        final var controllerPropertiesMap = configuration.getControllers();
        var controllerProps = controllerPropertiesMap.get(controller.getName());
        final var cfg = new ConfigurationWrapper(controller, controllerProps);
        this.controllers.put(controller.getName(), cfg);
        return controller;
    }
    
    @Override
    public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(ResourceController<R> controller) {
        return controllers.get(controller.getName());
    }
    
    @Override
  public ClientConfiguration getClientConfiguration() {
        return configuration.getClient();
    }
    
    private static class ConfigurationWrapper<R extends CustomResource> extends AnnotationConfiguration<R> {
        private final Optional<ControllerProperties> properties;
        
        private ConfigurationWrapper(ResourceController<R> controller, ControllerProperties properties) {
            super(controller);
            this.properties = Optional.ofNullable(properties);
        }
        
        @Override
        public String getName() {
            return super.getName();
        }
        
        @Override
        public String getCRDName() {
            return properties.map(ControllerProperties::getCRDName).orElse(super.getCRDName());
        }
        
        @Override
        public String getFinalizer() {
            return properties.map(ControllerProperties::getFinalizer).orElse(super.getFinalizer());
        }
        
        @Override
        public boolean isGenerationAware() {
            return properties.map(ControllerProperties::isGenerationAware).orElse(super.isGenerationAware());
        }
        
        @Override
        public Class<R> getCustomResourceClass() {
            return super.getCustomResourceClass();
    }
    
    @Override
    public boolean isClusterScoped() {
      return properties.map(ControllerProperties::isClusterScoped).orElse(super.isClusterScoped());
    }
    
        @Override
    public Set<String> getNamespaces() {
            return properties.map(ControllerProperties::getNamespaces).orElse(super.getNamespaces());
        }
        
        @Override
        public boolean watchAllNamespaces() {
            return super.watchAllNamespaces();
        }
        
        @Override
        public RetryConfiguration getRetryConfiguration() {
            return properties.map(ControllerProperties::getRetry).map(RetryProperties::asRetryConfiguration).orElse(RetryConfiguration.DEFAULT);
        }
  }
}
