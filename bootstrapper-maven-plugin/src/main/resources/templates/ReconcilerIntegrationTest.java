package {{groupId}};

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static {{groupId}}.ConfigMapDependentResource.KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class {{artifactClassId}}ReconcilerIntegrationTest {

    public static final String RESOURCE_NAME = "test1";
    public static final String INITIAL_VALUE = "initial value";
    public static final String CHANGED_VALUE = "changed value";

    @RegisterExtension
    LocallyRunOperatorExtension extension =
            LocallyRunOperatorExtension.builder()
                    .withReconciler({{artifactClassId}}Reconciler.class)
                    .build();

    @Test
    void testCRUDOperations() {
        var cr = extension.create(testResource());

        await().untilAsserted(() -> {
            var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
            assertThat(cm).isNotNull();
            assertThat(cm.getData()).containsEntry(KEY, INITIAL_VALUE);
        });

        cr.getSpec().setValue(CHANGED_VALUE);
        cr = extension.replace(cr);

        await().untilAsserted(() -> {
            var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
            assertThat(cm.getData()).containsEntry(KEY, CHANGED_VALUE);
        });

        extension.delete(cr);

        await().untilAsserted(() -> {
            var cm = extension.get(ConfigMap.class, RESOURCE_NAME);
            assertThat(cm).isNull();
        });
    }

    {{artifactClassId}}CustomResource testResource() {
        var resource = new {{artifactClassId}}CustomResource();
        resource.setMetadata(new ObjectMetaBuilder()
                .withName(RESOURCE_NAME)
                .build());
        resource.setSpec(new {{artifactClassId}}Spec());
        resource.getSpec().setValue(INITIAL_VALUE);
        return resource;
    }
}
