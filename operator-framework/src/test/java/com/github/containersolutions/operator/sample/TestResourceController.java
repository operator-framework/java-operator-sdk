package com.github.containersolutions.operator.sample;

import com.github.containersolutions.operator.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;

@Controller(kind = TestResourceController.KIND_NAME, customResourceClass = TestCustomResource.class)
public class TestResourceController implements ResourceController<TestCustomResource> {

    public static final String KIND_NAME = "customResourceDefinition";

    @Override
    public void deleteResource(TestCustomResource resource, Context<TestCustomResource> context) {
    }

    @Override
    public TestCustomResource createOrUpdateResource(TestCustomResource resource, Context<TestCustomResource> context) {
        return null;
    }
}
