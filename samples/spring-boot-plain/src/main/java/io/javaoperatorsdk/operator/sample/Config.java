package io.javaoperatorsdk.operator.sample;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;

@Configuration
public class Config {

  @Bean
  public CustomServiceController customServiceController() {
    return new CustomServiceController();
  }

  // Register all controller beans
  @Bean(initMethod = "start", destroyMethod = "stop")
  public Operator operator(List<Reconciler> controllers) {
    Operator operator = new Operator(DefaultConfigurationService.instance());
    controllers.forEach(operator::registerController);
    return operator;
  }
}
