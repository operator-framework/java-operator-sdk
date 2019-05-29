package jkube.operator;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import jkube.operator.api.CustomResourceController;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static jkube.operator.ControllerUtils.*;

public class Operator {

    private static Operator singletonOperator;

    private final KubernetesClient k8sClient;

    private Map<CustomResourceController, EventDispatcher> controllers = new HashMap<>();

    private final static Logger log = LoggerFactory.getLogger(Operator.class);


    public static Operator initializeFromEnvironment() {
        if (singletonOperator == null) {
            ConfigBuilder config = new ConfigBuilder().withTrustCerts(true);
            if (StringUtils.isNotBlank(System.getenv("K8S_MASTER_URL"))) {
                config.withMasterUrl(System.getenv("K8S_MASTER_URL"));
            }
            if (StringUtils.isNoneBlank(System.getenv("K8S_USERNAME"), System.getenv("K8S_PASSWORD"))) {
                config.withUsername(System.getenv("K8S_USERNAME")).withPassword(System.getenv("K8S_PASSWORD"));
            }
            singletonOperator = new Operator(new DefaultOpenShiftClient(config.build()));
        }
        return singletonOperator;
    }

    private Operator(KubernetesClient k8sClient) {
        this.k8sClient = k8sClient;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("Error", e);
            this.stop();
        });
    }

    public <R extends CustomResource, L extends CustomResourceList<R>, D extends CustomResourceDoneable<R>>

    void registerController(CustomResourceController<R> controller) throws OperatorException {
        Class<? extends CustomResource> resClass = getCustomResourceClass(controller);
        KubernetesDeserializer.registerCustomKind(getApiVersion(controller),
                resClass.getSimpleName(), resClass);

        CustomResourceDefinitionList crdList = k8sClient.customResourceDefinitions().list();
        Optional<CustomResourceDefinition> crd = crdList.getItems().stream()
                .filter(c -> resClass.getSimpleName().equals(c.getSpec().getNames().getKind()) &&
                        getCrdVersion(controller).equals(c.getSpec().getVersion()))
                .findFirst();

        if (crd.isPresent()) {
            MixedOperation client = k8sClient.customResources(crd.get(), resClass, CustomResourceList.class, CustomResourceDoneable.class);
            EventDispatcher<R, L, D> eventDispatcher = new EventDispatcher<>(controller, client);
            client.watch(eventDispatcher);
            controllers.put(controller, eventDispatcher);
            log.info("Registered Controller '" + controller.getClass().getSimpleName() + "' for CRD '"
                    + getCustomResourceClass(controller).getName() + "'");
        } else {
            throw new OperatorException("CRD '" + resClass.getSimpleName() + "' with version '"
                    + getCrdVersion(controller) + "' not found");
        }
    }

    public void stop() {
        k8sClient.close();
    }
}
