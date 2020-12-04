package io.javaoperatorsdk.operator.sample;

import io.javaoperatorsdk.operator.api.Controller;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Note that we have multiple options here either we can add this component scan as seen below. Or
 * annotate controllers with @Component or @Service annotation or just register the bean within a
 * spring "@Configuration".
 */
@ComponentScan(
    includeFilters = {
      @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Controller.class)
    })
@SpringBootApplication
public class SpringBootStarterSampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(SpringBootStarterSampleApplication.class, args);
  }
}
