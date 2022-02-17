package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KubernetesDependentResourceTest {

    private TemporalResourceCache temporalResourceCacheMock = mock(TemporalResourceCache.class);
    private InformerEventSource informerEventSourceMock = mock(InformerEventSource.class);

    KubernetesDependentResource<ConfigMap, TestCustomResource> kubernetesDependentResource = new KubernetesDependentResource() {
        {
            this.temporalResourceCache = temporalResourceCacheMock;
            this.informerEventSource = informerEventSourceMock;
        }
        @Override
        protected Object desired(HasMetadata primary, Context context) {
            return testResource();
        }
    };

    @BeforeEach
    public void setup() {

    }

    @Test
    void getResourceCheckTheTemporalCacheFirst() {

    }

    @Test
    void getResourceGetsResourceFromInformerIfNotInTemporalCache() {

    }

    TestCustomResource primaryResource() {
        TestCustomResource testCustomResource = new TestCustomResource();
        testCustomResource.setMetadata(new ObjectMeta());
        testCustomResource.getMetadata().setName("test");
        testCustomResource.getMetadata().setNamespace("default");
        return testCustomResource;
    }

    ConfigMap testResource() {
        ConfigMap configMap = new ConfigMap();
        configMap.setMetadata(new ObjectMeta());
        configMap.getMetadata().setName("test");
        configMap.getMetadata().setNamespace("default");
        configMap.getMetadata().setResourceVersion("0");
        return configMap;
    }

}