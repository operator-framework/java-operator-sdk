package jkube.operator;

import io.fabric8.kubernetes.client.CustomResource;
import jkube.operator.api.Controller;
import jkube.operator.api.ResourceController;

class ControllerUtils {

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).defaultFinalizer();
    }

    static Class<? extends CustomResource> getCustomResourceClass(ResourceController controller) {
        return getAnnotation(controller).customResourceClass();
    }

    static String getApiVersion(ResourceController controller) {
        return getAnnotation(controller).apiVersion();
    }

    static String getCrdVersion(ResourceController controller) {
        return getAnnotation(controller).crdVersion();
    }

    static String getCustomResourceDefinitionName(ResourceController controller) {
        return getAnnotation(controller).customResourceDefinitionName();
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }
}
