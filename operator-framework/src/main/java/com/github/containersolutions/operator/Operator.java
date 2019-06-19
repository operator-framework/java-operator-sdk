package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.internal.CustomResourceOperationsImpl;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.github.containersolutions.operator.ControllerUtils.*;

public class Operator {

    private final KubernetesClient k8sClient;

    private Map<ResourceController, EventDispatcher> controllers = new HashMap<>();
    private Map<Class<? extends CustomResource>, CustomResourceOperationsImpl> customResourceClients = new HashMap<>();

    private final static Logger log = LoggerFactory.getLogger(Operator.class);

    public Operator(KubernetesClient k8sClient) {
        this.k8sClient = k8sClient;
        setDefaultExceptionHandler();
    }

    private void setDefaultExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("Error", e);
            this.stop();
        });
    }

    public <R extends CustomResource> void registerController(ResourceController<R> controller) throws OperatorException {
        Class<R> resClass = getCustomResourceClass(controller);
        Optional<CustomResourceDefinition> crd = getCustomResourceDefinitionForController(controller);
        String kind = ControllerUtils.getKind(controller);
        KubernetesDeserializer.registerCustomKind(getApiVersion(controller), kind, resClass);

        if (crd.isPresent()) {
            Class<? extends CustomResourceList<R>> list = getCustomResourceListClass(controller);
            Class<? extends CustomResourceDoneable<R>> doneable = getCustomResourceDonebaleClass(controller);
            MixedOperation client = k8sClient.customResources(crd.get(), resClass, list, doneable);

            EventDispatcher<R> eventDispatcher =
                    new EventDispatcher<>(controller, (CustomResourceOperationsImpl) client, client, k8sClient,
                            ControllerUtils.getDefaultFinalizer(controller));
            client.watch(eventDispatcher);
            customResourceClients.put(resClass, (CustomResourceOperationsImpl) client);
            controllers.put(controller, eventDispatcher);
            log.info("Registered Controller '" + controller.getClass().getSimpleName() + "' for CRD '"
                    + getCustomResourceClass(controller).getName() + "'");
        } else {
            throw new OperatorException("CRD '" + resClass.getSimpleName() + "' with version '"
                    + getVersion(controller) + "' not found");
        }
    }

    private Optional<CustomResourceDefinition> getCustomResourceDefinitionForController(ResourceController controller) {
        CustomResourceDefinitionList crdList = k8sClient.customResourceDefinitions().list();

        return crdList.getItems().stream()
                .filter(c -> getKind(controller).equals(c.getSpec().getNames().getKind()) &&
                        getVersion(controller).equals(c.getSpec().getVersion()))
                .findFirst();
    }


    public void stop() {
        k8sClient.close();
    }

    public <T extends CustomResource> CustomResourceOperationsImpl getCustomResourceClients(Class<T> customResourceClass) {
        return customResourceClients.get(customResourceClass);
    }
}
