package com.github.containersolutions.operator.spingboot.starter;

import com.github.containersolutions.operator.Operator;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

// experimental will check later
//@Component
public class CustomResourceOperationRegistryPostprocessor implements BeanDefinitionRegistryPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(OperatorAutoConfiguration.class);

    @Autowired
    private Operator operator;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry beanDefinitionRegistry) throws BeansException {
        operator.getCustomResourceClients().entrySet().forEach(e -> {
            log.info("Registering CustomResourceOperationsImpl for kind: {}", e.getValue().getKind());
            beanDefinitionRegistry.registerBeanDefinition(e.getValue().getKind(), BeanDefinitionBuilder
                    .genericBeanDefinition(CustomResourceOperationsImpl.class,
                            () -> operator.getCustomResourceClients(e.getKey()))
                    .getBeanDefinition());
        });
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {

    }
}
