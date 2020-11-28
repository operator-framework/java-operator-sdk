package io.javaoperatorsdk.quarkus.extension.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class QuarkusExtensionProcessor {
    
    private static final String FEATURE = "operator-sdk";
    
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
