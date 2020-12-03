package io.javaoperatorsdk.quarkus.extension;

import java.util.Optional;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ClientConfiguration;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.quarkus.arc.DefaultBean;

@Singleton
@DefaultBean
public class QuarkusConfigurationService implements ConfigurationService {
    @Inject
    io.fabric8.kubernetes.client.KubernetesClient client;
    
    @Override
    public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(ResourceController<R> controller) {
        return null;
    }
    
    @Override
    public ClientConfiguration getClientConfiguration() {
        final var config = client.getConfiguration();
        return new ClientConfiguration() {
            @Override
            public boolean isOpenshift() {
                return false; // todo: fix
            }
            
            @Override
            public Optional<String> getUsername() {
                return Optional.ofNullable(config.getUsername());
            }
            
            @Override
            public Optional<String> getPassword() {
                return Optional.ofNullable(config.getPassword());
            }
            
            @Override
            public Optional<String> getMasterUrl() {
                return Optional.ofNullable(config.getMasterUrl());
            }
            
            @Override
            public boolean isTrustSelfSignedCertificates() {
                return config.isTrustCerts();
            }
        };
    }
    
    @DefaultBean
    @Singleton
    @Produces
    public Operator operator() {
        return new Operator(client, this);
    }
}
