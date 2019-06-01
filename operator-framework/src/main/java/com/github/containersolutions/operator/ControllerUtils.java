package com.github.containersolutions.operator;

import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import io.fabric8.kubernetes.client.CustomResource;

class ControllerUtils {

    public static final String GROUP_API_DELIMITER = "/";

    static String getDefaultFinalizer(ResourceController controller) {
        return getAnnotation(controller).defaultFinalizer();
    }

    static Class<? extends CustomResource> getCustomResourceClass(ResourceController controller) {
        return getAnnotation(controller).customResourceClass();
    }

    static String getApiVersion(ResourceController controller) {
        return getGroup(controller) + GROUP_API_DELIMITER + getAnnotation(controller).version();
    }

    private static String getGroup(ResourceController controller) {
        return getAnnotation(controller).group();
    }

    static String getVersion(ResourceController controller) {
        return getAnnotation(controller).version();
    }

    static String getKind(ResourceController controller) {
        return getAnnotation(controller).kind();
    }

    private static Controller getAnnotation(ResourceController controller) {
        return controller.getClass().getAnnotation(Controller.class);
    }
}
