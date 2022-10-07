package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.sample.externalstate.ExternalStateReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ExternalStateIT {

    @RegisterExtension
    LocallyRunOperatorExtension operator =
            LocallyRunOperatorExtension.builder().withReconciler(ExternalStateReconciler.class)
                    .build();

    @Test
    public void reconcilesResourceWithPersistentState() {

    }

}
