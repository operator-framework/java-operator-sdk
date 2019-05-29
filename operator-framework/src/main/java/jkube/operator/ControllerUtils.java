package jkube.operator;

import io.fabric8.kubernetes.client.CustomResource;
import jkube.operator.api.CustomResourceController;

class ControllerUtils {

    static String getDefaultFinalizer(CustomResourceController controller) {
        return null;
    }

    static Class<? extends CustomResource> getCustomResourceClass(CustomResourceController controller) {
        return null;
    }

    static String getApiVersion(CustomResourceController controller) {
        return null;
    }

    static String getCrdVersion(CustomResourceController controller) {
        return null;
    }
}
