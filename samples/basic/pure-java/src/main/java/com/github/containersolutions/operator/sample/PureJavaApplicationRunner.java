package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Operator;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

public class PureJavaApplicationRunner {

    public static void main(String[] args) {
        KubernetesClient client = new DefaultKubernetesClient();
        Operator operator = new Operator(client);
        operator.registerController(new CustomServiceController(client));
    }
}
