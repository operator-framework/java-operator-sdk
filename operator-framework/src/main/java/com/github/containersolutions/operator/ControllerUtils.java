package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.CustomResource;

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

    static String getKind(ResourceController controller) {
        return getAnnotation(controller).kind();
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }
}
