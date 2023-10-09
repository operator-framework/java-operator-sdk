package {{groupId}};

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import java.util.Map;
import java.util.Optional;

@ControllerConfiguration
public class {{artifactClassId}}Reconciler implements Reconciler<{{artifactClassId}}CustomResource>, EventSourceInitializer<{{artifactClassId}}CustomResource> {

    public static final String VALUE_KEY = "value";

    public UpdateControl<{{artifactClassId}}CustomResource> reconcile({{artifactClassId}}CustomResource primary,
                                                     Context<{{artifactClassId}}CustomResource> context) {
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

    private ConfigMap desiredConfigMap({{artifactClassId}}CustomResource primary) {
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
    public Map<String, EventSource> prepareEventSources(EventSourceContext<{{artifactClassId}}CustomResource> context) {
        return EventSourceInitializer.nameEventSources(
                new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context).build(), context));

    }
}
