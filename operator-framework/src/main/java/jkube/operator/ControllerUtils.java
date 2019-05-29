package jkube.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.CustomResourceDoneable;
import io.fabric8.kubernetes.client.CustomResourceList;
import jkube.operator.api.CustomResourceController;

class ControllerUtils {

    static String getDefaultFinalizer(CustomResourceController controller) {
        return null;
    }

    static Class<? extends CustomResource> getCustomResourceClass(CustomResourceController controller) {
        return null;
    }

    static Class<? extends CustomResourceList> getCustomResourceListClass(CustomResourceController controller) {
        return null;
    }

    static Class<? extends CustomResourceDoneable> getCustomResourceDoneableClass(CustomResourceController controller) {
        return null;
    }

    static String getApiVersion(CustomResourceController controller) {
        return null;
    }

    static String getCrdVersion(CustomResourceController controller) {
        return null;
    }
}
