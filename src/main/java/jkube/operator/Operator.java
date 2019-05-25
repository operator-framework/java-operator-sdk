package jkube.operator;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Operator {

    private KubernetesClient k8sClient;

    private Map<CustomResourceController, EventDispatcher> controllers = new HashMap<>();

    private final static Logger log = LoggerFactory.getLogger(Operator.class);

    public static Operator initializeFromEnvironment() {
        ConfigBuilder config = new ConfigBuilder().withTrustCerts(true);
        if (StringUtils.isNotBlank(System.getenv("K8S_MASTER_URL"))) {
            config.withMasterUrl(System.getenv("K8S_MASTER_URL"));
        }
        if (StringUtils.isNoneBlank(System.getenv("K8S_USERNAME"), System.getenv("K8S_PASSWORD"))) {
            config.withUsername(System.getenv("K8S_USERNAME")).withPassword(System.getenv("K8S_PASSWORD"));
        }
        var client = new DefaultOpenShiftClient(config.build());


        Operator operator = new Operator();
        operator.k8sClient = client;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            log.error("Error", e);
            operator.stop();
        });
        return operator;
    }

    private Operator() {
    }

    public <R extends CustomResource, L extends CustomResourceList<R>, D extends CustomResourceDoneable<R>>
    void registerController(CustomResourceController<R, L, D> controller) throws OperatorException {
        var resClass = controller.getCustomResourceClass();
        KubernetesDeserializer.registerCustomKind(controller.getApiVersion(),
                resClass.getSimpleName(), resClass);

        CustomResourceDefinitionList crdList = k8sClient.customResourceDefinitions().list();
        var crd = crdList.getItems().stream()
                .filter(c -> resClass.getSimpleName().equals(c.getSpec().getNames().getKind()) &&
                        controller.getCrdVersion().equals(c.getSpec().getVersion()))
                .findFirst();

        if (crd.isPresent()) {
            var client = k8sClient.customResources(crd.get(), resClass, controller.getCustomResourceListClass(),
                    controller.getCustomResourceDoneableClass());
            var eventDispatcher = new EventDispatcher<>(controller, client);
            client.watch(eventDispatcher);
            controllers.put(controller, eventDispatcher);
            log.info("Registered Controller '" + controller.getClass().getSimpleName() + "' for CRD '" + controller.getCustomResourceClass().getName() + "'");
        } else {
            throw new OperatorException("CRD '" + resClass.getSimpleName() + "' with version '" + controller.getCrdVersion() + "' not found");
        }
    }

    public void stop() {
        k8sClient.close();
    }
}
