package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.junit.jupiter.api.Test;

public class DelmeTest {

    @Test
    void test() throws InterruptedException {
        var client = new KubernetesClientBuilder().build();
        client.getNamespace();

        var informer = client.pods().inform();
        informer.addEventHandler(new ResourceEventHandler<Pod>() {
            @Override
            public void onAdd(Pod pod) {
                System.out.println("added "+pod);
            }

            @Override
            public void onUpdate(Pod pod, Pod t1) {
                System.out.println("updated "+pod);
            }

            @Override
            public void onDelete(Pod pod, boolean b) {
                System.out.println("deleted "+pod);
            }
        });

        System.out.println("started client");

        Thread.sleep(511000);
    }

}
