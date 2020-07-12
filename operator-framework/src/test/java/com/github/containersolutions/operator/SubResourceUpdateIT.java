package com.github.containersolutions.operator;

import com.github.containersolutions.operator.sample.subresource.SubResourceTestCustomResource;
import com.github.containersolutions.operator.sample.subresource.SubResourceTestCustomResourceController;
import com.github.containersolutions.operator.sample.subresource.SubResourceTestCustomResourceSpec;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.concurrent.TimeUnit;

import static com.github.containersolutions.operator.IntegrationTestSupport.TEST_NAMESPACE;
import static com.github.containersolutions.operator.api.Controller.DEFAULT_FINALIZER;
import static com.github.containersolutions.operator.sample.subresource.SubResourceTestCustomResourceStatus.State.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SubResourceUpdateIT {

    private IntegrationTestSupport integrationTestSupport = new IntegrationTestSupport();

    @BeforeEach
    public void initAndCleanup() {
        KubernetesClient k8sClient = new DefaultKubernetesClient();
        integrationTestSupport.initialize(k8sClient, new SubResourceTestCustomResourceController(),
                "subresource-test-crd.yaml");
        integrationTestSupport.cleanup();
    }

    @Test
    public void updatesSubResourceStatus() {
        integrationTestSupport.teardownIfSuccess(() -> {
            SubResourceTestCustomResource resource = createTestCustomResource("1");
            integrationTestSupport.getCrOperations().inNamespace(TEST_NAMESPACE).create(resource);

            awaitStatusUpdated(resource.getMetadata().getName());
            // wait for sure, there are no more events
            waitXms(200);
            // there is no event on status update processed
            assertThat(integrationTestSupport.numberOfControllerExecutions()).isEqualTo(2);
        });
    }

    /**
     * Note that we check on controller impl if there is finalizer on execution.
     */
    @Test
    public void ifNoFinalizerPresentFirstAddsTheFinalizerThenExecutesControllerAgain() {
        integrationTestSupport.teardownIfSuccess(() -> {
            SubResourceTestCustomResource resource = createTestCustomResource("1");
            resource.getMetadata().getFinalizers().clear();
            integrationTestSupport.getCrOperations().inNamespace(TEST_NAMESPACE).create(resource);

            awaitStatusUpdated(resource.getMetadata().getName());
            // wait for sure, there are no more events
            waitXms(200);
            // there is no event on status update processed
            assertThat(integrationTestSupport.numberOfControllerExecutions()).isEqualTo(2);
        });
    }

    /**
     * Not that here status sub-resource update will fail on optimistic locking. This solves a tricky situation:
     * If this would not happen (no optimistic locking on status sub-resource)  we could receive and store an event
     * while processing the controller method. But this event would always fail since its resource version is outdated
     * already.
     * */
    @Test
    public void updateCustomResourceAfterSubResourceChange() {
        integrationTestSupport.teardownIfSuccess(() -> {
            SubResourceTestCustomResource resource = createTestCustomResource("1");
            integrationTestSupport.getCrOperations().inNamespace(TEST_NAMESPACE).create(resource);

            resource.getSpec().setValue("new value");
            integrationTestSupport.getCrOperations().inNamespace(TEST_NAMESPACE).createOrReplace(resource);

            awaitStatusUpdated(resource.getMetadata().getName());

            // wait for sure, there are no more events
            waitXms(200);
            // there is no event on status update processed
            assertThat(integrationTestSupport.numberOfControllerExecutions()).isEqualTo(3);
        });

    }

    void awaitStatusUpdated(String name) {
        await("cr status updated").atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    SubResourceTestCustomResource cr = (SubResourceTestCustomResource) integrationTestSupport.getCrOperations().inNamespace(TEST_NAMESPACE).withName(name).get();
                    assertThat(cr.getMetadata().getFinalizers()).hasSize(1);
                    assertThat(cr).isNotNull();
                    assertThat(cr.getStatus()).isNotNull();
                    assertThat(cr.getStatus().getState()).isEqualTo(SUCCESS);
                });
    }

    public SubResourceTestCustomResource createTestCustomResource(String id) {
        SubResourceTestCustomResource resource = new SubResourceTestCustomResource();
        resource.setMetadata(new ObjectMetaBuilder()
                .withName("subresource-" + id)
                .withNamespace(TEST_NAMESPACE)
                .withFinalizers(DEFAULT_FINALIZER)
                .build());
        resource.setKind("SubresourceSample");
        resource.setSpec(new SubResourceTestCustomResourceSpec());
        resource.getSpec().setValue(id);
        return resource;
    }

    private void waitXms(int x) {
        try {
            Thread.sleep(x);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
