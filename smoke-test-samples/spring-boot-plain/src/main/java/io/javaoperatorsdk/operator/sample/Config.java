package io.javaoperatorsdk.operator.sample;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@Configuration
public class Config {

  @Bean
  public CustomServiceReconciler customServiceController() {
    return new CustomServiceReconciler();
  }

  // Register all controller beans
  @Bean(initMethod = "start", destroyMethod = "stop")
  @SuppressWarnings("rawtypes")
  public Operator operator(List<Reconciler> controllers) {
    Operator operator = new Operator(ConfigurationServiceProvider.instance());
    controllers.forEach(operator::register);
    return operator;
  }
}
