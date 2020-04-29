package com.github.containersolutions.operator.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Note that we have multiple options here either we can add this component scan as seen below. Or annotate controllers
 * with @Component or @Service annotation or just register the bean within a spring "@Configuration".
 */
@SpringBootApplication
public class SpringBootStarterSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootStarterSampleApplication.class, args);
    }

}
