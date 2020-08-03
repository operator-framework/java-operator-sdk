package com.github.containersolutions.operator.spingboot.starter;

import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;
import com.github.containersolutions.operator.spingboot.starter.model.TestResource;
import io.fabric8.kubernetes.client.CustomResource;
import org.springframework.stereotype.Component;

@Component
@Controller(crdName = "name",
        customResourceClass = TestResource.class)
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
