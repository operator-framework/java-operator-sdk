package io.javaoperatorsdk.sample;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Map;
import java.util.Optional;

@ControllerConfiguration
public class MyReconciler implements Reconciler<MyCustomResource>, EventSourceInitializer<MyCustomResource> {

    public static final String VALUE_KEY = "value";

    public UpdateControl<MyCustomResource> reconcile(MyCustomResource primary,
                                                     Context<MyCustomResource> context) {

        Optional<ConfigMap> configMap = context.getSecondaryResource(ConfigMap.class);
        configMap.ifPresentOrElse(actual -> {
            var desired = desiredConfigMap(primary);
            if (!match(actual, desired)) {
                context.getClient().resource(desired).update();
            }
        }, () -> context.getClient().resource(desiredConfigMap(primary))
                .create());

        return UpdateControl.noUpdate();
    }

    private boolean match(ConfigMap actual, ConfigMap desired) {
        return actual.getData().equals(desired.getData());
    }

    private ConfigMap desiredConfigMap(MyCustomResource primary) {
        var cm = new ConfigMapBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(primary.getMetadata().getName())
                        .withNamespace(primary.getMetadata().getNamespace())
                        .build())
                .withData(Map.of(VALUE_KEY, primary.getSpec().getValue()))
                .build();
        cm.addOwnerReference(primary);
        return cm;
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<MyCustomResource> context) {
        return EventSourceInitializer.nameEventSources(
                new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context).build(), context));

    }
}
