package com.github.containersolutions.operator.processing;

import io.fabric8.kubernetes.client.CustomResource;

public class ProcessingUtils {

    public static String getUID(CustomResource customResource) {
        return customResource.getMetadata().getUid();
    }

}
