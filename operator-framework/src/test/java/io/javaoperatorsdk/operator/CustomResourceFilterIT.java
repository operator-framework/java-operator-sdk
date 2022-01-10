package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.customfilter.CustomFilteringTestReconciler;
import io.javaoperatorsdk.operator.sample.simple.TestReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CustomResourceFilterIT {

    @RegisterExtension
    OperatorExtension operator =
            OperatorExtension.builder()
                    .withConfigurationService(DefaultConfigurationService.instance())
                    .withReconciler(new CustomFilteringTestReconciler())
                    .build();

    @Test
    public void customFiltering() {



    }


}
