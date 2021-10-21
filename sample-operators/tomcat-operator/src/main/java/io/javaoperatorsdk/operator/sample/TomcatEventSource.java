package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

/**
 * Used by the WebappController to watch changes on Tomcat objects
 */
public class TomcatEventSource extends AbstractEventSource implements Watcher<Tomcat> {
    private static final Logger log = LoggerFactory.getLogger(TomcatEventSource.class);

    private final KubernetesClient client;

    public static TomcatEventSource createAndRegisterWatch(KubernetesClient client) {
        TomcatEventSource tomcatEventSource = new TomcatEventSource(client);
        tomcatEventSource.registerWatch();
        return tomcatEventSource;
    }

    private TomcatEventSource(KubernetesClient client) {
        this.client = client;
    }

    private void registerWatch() {
        var tomcatClient = client.customResources(Tomcat.class);
        tomcatClient.inAnyNamespace().watch(this);
    }

    @Override
    public void eventReceived(Action action, Tomcat tomcat) {
        log.info("Event received for action: {}, Tomcat: {}", action.name(), tomcat.getMetadata().getName());

        if (action == Action.ERROR) {
            log.warn(
                    "Skipping {} event for custom resource uid: {}, version: {}",
                    action,
                    getUID(tomcat),
                    getVersion(tomcat));
            return;
        }

        var webappClient = client.customResources(Webapp.class);
        Optional<Webapp> webapp = webappClient.inNamespace(tomcat.getMetadata().getNamespace())
                .list().getItems().stream()
                .filter(wapp -> wapp.getSpec().getTomcat().equals(tomcat.getMetadata().getName()))
                .findFirst();

        if (webapp.isPresent()) {
            eventHandler.handleEvent(new TomcatEvent(action, tomcat, this,
                    webapp.get().getMetadata().getUid()));
        } else {
            log.debug("Webapp not found for Tomcat {}", tomcat.getMetadata().getName());
        }
    }

    @Override
    public void onClose(WatcherException e) {
        if (e == null) {
            return;
        }
        if (e.isHttpGone()) {
            log.warn("Received error for watch, will try to reconnect.", e);
            registerWatch();
        } else {
            // Note that this should not happen normally, since fabric8 client handles reconnect.
            // In case it tries to reconnect this method is not called.
            log.error("Unexpected error happened with watch. Will exit.", e);
            System.exit(1);
        }
    }
}
