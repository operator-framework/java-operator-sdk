package io.javaoperatorsdk.operator.spingboot.starter;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.*;
import io.javaoperatorsdk.operator.spingboot.starter.model.TestResource;
import org.springframework.stereotype.Component;

@Component
@Controller(crdName = "name",
        customResourceClass = TestResource.class)
public class TestController implements ResourceController {

    @Override
    public DeleteControl deleteResource(CustomResource resource, Context context) {
        return new DeleteControl();
    }

    @Override
    public UpdateControl createOrUpdateResource(CustomResource resource, Context context) {
        return UpdateControl.noUpdate();
    }
}
