package io.javaoperatorsdk.operator.springboot.starter;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.springboot.starter.model.TestResource;
import org.springframework.stereotype.Component;

@Component
@Controller(crdName = "name")
public class TestController implements ResourceController {

    @Override
    public boolean deleteResource(CustomResource resource, Context context) {
        return true;
    }

    @Override
    public UpdateControl createOrUpdateResource(CustomResource resource, Context context) {
        return UpdateControl.noUpdate();
    }
}
