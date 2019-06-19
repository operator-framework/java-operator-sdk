package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Operator;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Runner {

    @Autowired
    private Operator operator;

    public static void main(String[] args) {
        SpringApplication.run(Runner.class, args);
    }
}
